package zone.moddev.mc.lib.machine.feature;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

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
   * The saved form: the running cycle, if any, and its progress.
   *
   * @param cycle the running cycle
   * @param progress ticks of {@code cycle} done
   */
  public record State(Optional<CyclePlan> cycle, int progress) {
    /** The save form. */
    public static final Codec<State> CODEC =
        RecordCodecBuilder.create(
            instance ->
                instance
                    .group(
                        CyclePlan.CODEC.optionalFieldOf("cycle").forGetter(State::cycle),
                        Codec.intRange(0, Integer.MAX_VALUE)
                            .optionalFieldOf("progress", 0)
                            .forGetter(State::progress))
                    .apply(instance, State::new));
  }

  private final String key;
  private final MachineInventory inventory;
  private final MachineRecipeLookup lookup;
  @Nullable private CyclePlan cycle;
  private int progress;
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
    return new State(Optional.ofNullable(cycle), progress);
  }

  /** Takes on a saved cycle if its reservations can be put back; otherwise goes idle. */
  @Override
  public void restore(State state) {
    if (cycle != null) cycle.release(inventory);
    cycle = state.cycle().filter(plan -> plan.restore(inventory)).orElse(null);
    progress = cycle == null ? 0 : Math.min(state.progress(), cycle.time());
    status = cycle == null ? Status.IDLE : Status.RUNNING;
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
