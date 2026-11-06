package zone.moddev.mc.lib.machine.feature;

import java.util.Optional;
import java.util.List;
import java.util.function.IntSupplier;
import net.minecraft.network.chat.Component;
import zone.moddev.mc.lib.api.redstone.IRedstoneOutput;
import zone.moddev.mc.lib.api.redstone.RedstoneOutput;
import zone.moddev.mc.lib.api.redstone.RedstoneControl;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import zone.moddev.mc.lib.machine.inventory.EnergyGroup;
import zone.moddev.mc.lib.machine.inventory.MachineInventory;
import zone.moddev.mc.lib.machine.recipe.CyclePlan;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeEntry;
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
public final class RecipeProcessor
    implements MachineFeature<RecipeProcessor.State>, IRedstoneOutput {
  private static final int SEARCH_RETRY_INTERVAL = 20;

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

  /** A status by name; a name it doesn't know is a decoding error, not an exception. */
  private static final Codec<Status> STATUS_CODEC =
      Codec.STRING.comapFlatMap(
          name -> {
            try {
              return DataResult.success(Status.valueOf(name));
            } catch (IllegalArgumentException ex) {
              return DataResult.error(() -> "Unknown processor status " + name);
            }
          },
          Status::name);

  /**
   * The saved form. A cycle that won't decode, say because an item is gone from the game, is
   * dropped as a whole while the rest of the state is kept.
   *
   * @param paused whether an interrupt holds the processor
   * @param progress ticks done of {@code cycle}, or of the recipe to resume
   * @param resumeRecipe the recipe a pause or a resume is waiting to reserve again
   * @param resumeTime the time that recipe took, so progress is dropped if its time changed
   * @param cycle the running cycle
   * @param status what the processor was doing; loading works it out again, and only a client takes
   *     it from here. A name this version doesn't know reads as empty, which loses nothing else.
   */
  public record State(
      boolean paused,
      int progress,
      Optional<ResourceLocation> resumeRecipe,
      int resumeTime,
      Optional<CyclePlan> cycle,
      Optional<Status> status) {
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
                        Codec.intRange(0, Integer.MAX_VALUE)
                            .optionalFieldOf("resume_time", 0)
                            .forGetter(State::resumeTime),
                        CyclePlan.CODEC.lenientOptionalFieldOf("cycle").forGetter(State::cycle),
                        STATUS_CODEC.lenientOptionalFieldOf("status").forGetter(State::status))
                    .apply(instance, State::new));
  }

  private final String key;
  private final MachineInventory inventory;
  private final MachineRecipeType type;
  private final MachineRecipeLookup lookup;
  @Nullable private CyclePlan cycle;
  private int progress;
  private boolean redstoneRunning;
  private final List<RedstoneOutput> redstoneOutputs;
  @Nullable private ResourceLocation resumeRecipe;
  private int resumeTime;
  private boolean paused;
  private boolean changed;
  private Status status = Status.IDLE;
  private Status marked = Status.IDLE;
  private int markedTime;
  @Nullable private CyclePlan justFinished;
  private int syncedTime;
  // Failed searches retry once per second, including changes in custom groups and recipe reloads.
  private int searchDelay;
  private long[] searchedRevisions = new long[0];

  /** A processor saved under {@code key} that runs recipes of {@code type} on {@code inventory}. */
  public RecipeProcessor(String key, MachineRecipeType type, MachineInventory inventory) {
    this(key, type, inventory, Component.literal(key));
  }

  /** A processor with a translated display name for its redstone output entries. */
  public RecipeProcessor(
      String key, MachineRecipeType type, MachineInventory inventory, Component displayName) {
    this.key = key;
    this.inventory = inventory;
    this.type = type;
    lookup = type.lookup();
    redstoneOutputs =
        List.of(
            output(
                "processing", displayName, "icon_resume", 0x64d870, () -> redstoneRunning ? 15 : 0),
            output("inactive", displayName, "icon_pause", 0xf0c646, () -> redstoneRunning ? 0 : 15),
            output(
                "progress",
                displayName,
                "icon_processor_progress",
                0x85d2ff,
                this::redstoneProgress));
  }

  private RedstoneOutput output(
      String kind, Component name, String icon, int colour, IntSupplier value) {
    return new RedstoneOutput(
        "processor/" + key + "/" + kind,
        Component.translatable("screen.mmdlib.redstone.source." + kind, name),
        Component.translatable("screen.mmdlib.redstone.source." + kind + ".info", name),
        colour,
        Optional.of(ResourceLocation.fromNamespaceAndPath("mmdlib", icon)),
        value);
  }

  @Override
  public List<RedstoneOutput> redstoneOutputs() {
    return redstoneOutputs;
  }

  private int redstoneProgress() {
    if (justFinished != null) return 15;
    int duration = cycle != null ? cycle.time() : resumeRecipe != null ? resumeTime : 0;
    return duration > 0 ? RedstoneControl.level((double) progress / duration) : 0;
  }

  @Override
  public void suspended(FeatureHost host) {
    redstoneRunning = false;
    justFinished = null;
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
        paused,
        progress,
        Optional.ofNullable(resumeRecipe),
        resumeTime,
        Optional.ofNullable(cycle),
        Optional.of(status));
  }

  @Override
  public boolean syncsToClient() {
    return true;
  }

  /**
   * Takes on the state a client is sent: the status, whether it is paused, the progress as of the
   * send and the time of the cycle. The reservations are the server's, so the cycle is not put back
   * and nothing is reserved. While paused, {@link #time} is 0 and {@link #progress} is the ticks
   * kept, so a progress bar should not divide by {@code time()} without checking it.
   */
  @Override
  public void restoreOnClient(State state) {
    paused = state.paused();
    progress = state.progress();
    state.status().ifPresent(sent -> status = sent);
    syncedTime = state.cycle().map(CyclePlan::time).orElse(0);
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
    syncedTime = 0;
    justFinished = null;
    redstoneRunning = false;
    paused = state.paused();
    searchDelay = 0;
    resumeRecipe = state.resumeRecipe().orElse(null);
    resumeTime = state.resumeTime();
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

  /**
   * Pauses the processor: stops drawing energy, releases the running cycle's reservations so its
   * items can be taken, and starts nothing new until {@link #resume}. Works without power and
   * whatever the status. The recipe and progress are kept for the resume.
   */
  public void interrupt() {
    if (paused) return;
    if (cycle != null) {
      cycle.release(inventory);
      resumeRecipe = cycle.recipe();
      resumeTime = cycle.time();
      cycle = null;
    }
    paused = true;
    redstoneRunning = false;
    status = Status.PAUSED;
    justFinished = null;
    changed = true;
  }

  /**
   * Lets a paused processor run again. The next tick reserves the paused recipe again if it still
   * can, and continues its progress; otherwise progress is lost and the processor searches.
   */
  public void resume() {
    if (!paused) return;
    paused = false;
    redstoneRunning = false;
    searchDelay = 0;
    status = Status.IDLE;
    changed = true;
  }

  /** Cancels work, releases reservations and forgets progress and pause state. */
  public void reset() {
    if (cycle != null) cycle.release(inventory);
    cycle = null;
    resumeRecipe = null;
    resumeTime = 0;
    progress = 0;
    syncedTime = 0;
    justFinished = null;
    paused = false;
    redstoneRunning = false;
    searchDelay = 0;
    status = Status.IDLE;
    changed = true;
  }

  /** What the processor is doing. */
  public Status status() {
    return status;
  }

  /** Ticks of the running cycle done. */
  public int progress() {
    return progress;
  }

  /**
   * Ticks the running cycle takes; 0 when idle, and also 0 when paused, though {@link #progress}
   * then holds the kept ticks. On a client, {@code status} and this are current, and {@code
   * progress} is as of the last status change.
   */
  public int time() {
    return cycle == null ? syncedTime : cycle.time();
  }

  /**
   * Runs a tick, then asks for a sync if what clients see differs from the last thing asked for. On
   * the tick that finishes a cycle the processor is idle only until the next tick starts the next
   * cycle; clients are shown that tick as the cycle still running, so they never read the gap.
   */
  @Override
  public void tick(FeatureHost host) {
    justFinished = null;
    run(host);
    redstoneRunning = !paused && (status == Status.RUNNING || justFinished != null);
    var seen = justFinished != null ? Status.RUNNING : status;
    int seenTime = justFinished != null ? justFinished.time() : time();
    if (seen != marked || seenTime != markedTime) {
      marked = seen;
      markedTime = seenTime;
      host.markSync();
    }
  }

  /**
   * What clients are sent. It is {@link #state} except on the tick that finishes a cycle, when the
   * finished cycle still shows as running so the one idle tick before the next cycle starts is not
   * sent. That tick is never saved: {@link #state} says idle and holds no cycle.
   */
  @Override
  public State clientState() {
    if (justFinished == null) return state();
    return new State(
        false,
        justFinished.time(),
        Optional.empty(),
        0,
        Optional.of(justFinished),
        Optional.of(Status.RUNNING));
  }

  private void run(FeatureHost host) {
    if (changed) {
      changed = false;
      host.markChanged();
    }
    if (paused) {
      status = Status.PAUSED;
      return;
    }
    var level = host.level();
    if (cycle == null && (resumeRecipe == null || !reserveAgain(level, host))) {
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
        justFinished = cycle;
        cycle = null;
        progress = 0;
        status = Status.IDLE;
        host.markChanged();
      } else {
        status = Status.BLOCKED;
      }
    }
  }

  /**
   * Reserves the paused recipe again if it still exists and fits. Progress is kept only if the
   * recipe takes as long as it did; if it can't be reserved, the processor searches instead.
   */
  private boolean reserveAgain(Level level, FeatureHost host) {
    var id = resumeRecipe;
    resumeRecipe = null;
    host.markChanged();
    CyclePlan plan = null;
    var holder = level.getRecipeManager().byKey(id).orElse(null);
    if (holder != null
        && holder.value() instanceof MachineRecipeEntry entry
        && entry.type() == type) {
      plan =
          CyclePlan.of(id, entry.recipe(), inventory)
              .filter(found -> found.reserve(inventory))
              .orElse(null);
    }
    if (plan == null) {
      progress = 0;
      return false;
    }
    cycle = plan;
    if (plan.time() != resumeTime) progress = 0;
    return true;
  }

  /** Reserves a cycle if a recipe can run. The starting tick draws no energy. */
  private void start(Level level, FeatureHost host) {
    if (searchDelay > 0 && inventory.unchanged(searchedRevisions)) {
      searchDelay--;
      return;
    }
    var found = lookup.find(level, inventory).orElse(null);
    var plan =
        found == null
            ? null
            : CyclePlan.of(found.id(), found.value().recipe(), inventory).orElse(null);
    if (plan == null || !plan.reserve(inventory)) {
      searchDelay = SEARCH_RETRY_INTERVAL - 1;
      searchedRevisions = inventory.revisions();
      return;
    }
    cycle = plan;
    searchDelay = 0;
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
