package zone.moddev.mc.lib.machine.feature;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import zone.moddev.mc.lib.machine.inventory.EnergyGroup;
import zone.moddev.mc.lib.machine.inventory.MachineInventory;
import zone.moddev.mc.lib.machine.recipe.CyclePlan;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeLookup;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeType;

/**
 * Runs the recipes of one type on a machine's inventory, one cycle at a time. A cycle reserves its
 * inputs and the room for its products when it starts, draws its energy evenly as it runs, and
 * takes the inputs and makes the products in one step at the end. Nothing leaves a slot or tank
 * before then, so a cycle that never finishes costs only the energy it used.
 *
 * <p>Several processors may share an inventory; a processor sees what the others reserved.
 */
public final class RecipeProcessor implements MachineFeature<RecipeProcessor.State> {
  /** What the processor is doing. */
  public enum Status {
    /** No cycle. */
    IDLE,
    /** A cycle is running. */
    RUNNING,
    /** A cycle is waiting for energy; it keeps its progress and reservations. */
    STALLED,
    /** A cycle has run its time but cannot finish, say an input was taken; retried each tick. */
    BLOCKED,
    /** Held by an interrupt. */
    PAUSED
  }

  /**
   * The saved form. A cycle that won't decode, say because an item is gone from the game, is
   * dropped as a whole while the rest of the state is kept.
   *
   * @param paused whether an interrupt holds the processor
   * @param progress ticks done of {@code cycle}, or of the recipe to resume
   * @param resumeRecipe the recipe a pause or a resume is waiting to reserve again
   * @param cycle the running cycle
   */
  public record State(
      boolean paused,
      int progress,
      Optional<ResourceLocation> resumeRecipe,
      Optional<CyclePlan> cycle) {
    /** The save form. */
    public static final Codec<State> CODEC =
        RecordCodecBuilder.create(
            instance ->
                instance
                    .group(
                        Codec.BOOL.optionalFieldOf("paused", false).forGetter(State::paused),
                        Codec.intRange(0, Integer.MAX_VALUE)
                            .optionalFieldOf("progress", 0)
                            .forGetter(State::progress),
                        ResourceLocation.CODEC
                            .optionalFieldOf("resume_recipe")
                            .forGetter(State::resumeRecipe),
                        CyclePlan.CODEC.lenientOptionalFieldOf("cycle").forGetter(State::cycle))
                    .apply(instance, State::new));
  }

  private final String key;
  private final MachineInventory inventory;
  private final MachineRecipeLookup lookup;
  @Nullable private CyclePlan cycle;
  private int progress;
  @Nullable private ResourceLocation resumeRecipe;
  private boolean paused;
  private Status status = Status.IDLE;

  /** A processor saved under {@code key} that runs recipes of {@code type} on {@code inventory}. */
  public RecipeProcessor(String key, MachineRecipeType type, MachineInventory inventory) {
    this.key = key;
    this.inventory = inventory;
    lookup = type.lookup();
  }

  @Override
  public String key() {
    return key;
  }

  @Override
  public Codec<State> codec() {
    return State.CODEC;
  }

  @Override
  public State state() {
    return new State(
        paused, progress, Optional.ofNullable(resumeRecipe), Optional.ofNullable(cycle));
  }

  /**
   * Takes on saved state. A running cycle is released first, since the storages keep their plans
   * when they load their contents. A saved cycle whose plan can't be put back cancels, and the
   * processor goes back to searching.
   *
   * <p>Load the storages before the {@link FeatureHost} loads, so the inputs are in place for the
   * plan to check.
   */
  @Override
  public void restore(State state) {
    if (cycle != null) cycle.release(inventory);
    cycle = null;
    paused = state.paused();
    resumeRecipe = state.resumeRecipe().orElse(null);
    progress = resumeRecipe == null ? 0 : state.progress();
    var saved = state.cycle().filter(plan -> !paused && holds(plan)).orElse(null);
    if (saved != null) {
      cycle = saved;
      progress = Math.min(state.progress(), saved.time());
    }
    status = paused ? Status.PAUSED : cycle == null ? Status.IDLE : Status.RUNNING;
  }

  /** Whether the plan's energy groups are there and its reservations can be put back. */
  private boolean holds(CyclePlan plan) {
    for (var key : plan.energy().keySet())
      if (!(inventory.group(key).orElse(null) instanceof EnergyGroup)) return false;
    return plan.restore(inventory);
  }

  /** Whether an interrupt holds the processor. */
  public boolean paused() {
    return paused;
  }

  /** What the processor is doing. */
  public Status status() {
    return status;
  }

  /** Ticks of the running cycle done. */
  public int progress() {
    return progress;
  }

  /** Ticks the running cycle takes; 0 when idle. */
  public int time() {
    return cycle == null ? 0 : cycle.time();
  }

  @Override
  public void tick(FeatureHost host) {
    if (paused) {
      status = Status.PAUSED;
      return;
    }
    var level = host.level();
    if (cycle == null) {
      start(level, host);
      return;
    }
    if (progress < cycle.time()) {
      if (!draw()) {
        status = Status.STALLED;
        return;
      }
      progress++;
      status = Status.RUNNING;
      host.markChanged();
    }
    if (progress >= cycle.time()) {
      if (cycle.finish(inventory, level.getRandom())) {
        cycle = null;
        progress = 0;
        status = Status.IDLE;
        host.markChanged();
      } else {
        status = Status.BLOCKED;
      }
    }
  }

  /** Reserves a cycle if a recipe can run. The starting tick draws no energy. */
  private void start(Level level, FeatureHost host) {
    var found = lookup.find(level, inventory).orElse(null);
    if (found == null) return;
    var plan = CyclePlan.of(found.id(), found.value().recipe(), inventory).orElse(null);
    if (plan == null || !plan.reserve(inventory)) return;
    cycle = plan;
    progress = 0;
    status = Status.RUNNING;
    host.markChanged();
  }

  /** Draws this tick's energy from every energy group, or none of it. */
  private boolean draw() {
    for (var need : cycle.energy().entrySet()) {
      long share = EnergySchedule.share(need.getValue(), cycle.time(), progress);
      if (!(inventory.group(need.getKey()).orElse(null) instanceof EnergyGroup group)
          || group.stored() < share) return false;
    }
    try (var tx = inventory.begin()) {
      for (var need : cycle.energy().entrySet()) {
        long share = EnergySchedule.share(need.getValue(), cycle.time(), progress);
        if (!(inventory.group(need.getKey()).orElse(null) instanceof EnergyGroup group)
            || group.extract(share, tx) < share) return false;
      }
      tx.commit();
      return true;
    }
  }
}
