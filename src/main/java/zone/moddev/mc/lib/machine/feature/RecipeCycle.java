package zone.moddev.mc.lib.machine.feature;

import java.util.Objects;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import zone.moddev.mc.lib.machine.inventory.EnergyGroup;
import zone.moddev.mc.lib.machine.inventory.MachineInventory;
import zone.moddev.mc.lib.machine.recipe.CyclePlan;
import zone.moddev.mc.lib.machine.recipe.MachineRecipe;

/**
 * Executes one explicitly supplied recipe against stable inventory groups.
 * Selection, demand, pause and tick ordering belong to the caller. Reserving
 * never advances time or spends energy; only {@link #advance} does that.
 */
public final class RecipeCycle {
    public record State(Optional<CyclePlan> plan, int progress) {
        public static final Codec<State> CODEC = RecordCodecBuilder
            .create(i -> i.group(
                CyclePlan.CODEC.lenientOptionalFieldOf("cycle")
                    .forGetter(State::plan),
                Codec.intRange(0, Integer.MAX_VALUE)
                    .optionalFieldOf("progress", 0).forGetter(State::progress))
                .apply(i, State::new));

        public State {
            Objects.requireNonNull(plan);
            if (progress < 0)
                throw new IllegalArgumentException("Negative cycle progress");
        }
    }

    public enum Result {
        IDLE, RUNNING, STALLED, BLOCKED, COMPLETED
    }

    private final MachineInventory inventory;
    private CyclePlan plan;
    private int progress;

    public RecipeCycle(MachineInventory inventory) {
        this.inventory = Objects.requireNonNull(inventory);
    }

    public Optional<CyclePlan> plan() {
        return Optional.ofNullable(plan);
    }

    public boolean active() {
        return plan != null;
    }

    public int progress() {
        return progress;
    }

    public int time() {
        return plan == null ? 0 : plan.time();
    }

    public State state() {
        return new State(plan(), progress);
    }

    /**
     * Claims inputs/output space, even without power. Callers that require
     * power before admission can first use RecipeMatcher. Active work is never
     * replaced. On failure, neither storage nor runner state changes.
     */
    public boolean start(ResourceLocation id, MachineRecipe recipe) {
        return start(id, recipe, false, 0, 0);
    }

    /** Reclaims interrupted work; changed duration discards paid progress. */
    public boolean resume(ResourceLocation id, MachineRecipe recipe,
        int paidProgress, int previousTime) {
        return start(id, recipe, true, paidProgress, previousTime);
    }

    private boolean start(ResourceLocation id, MachineRecipe recipe,
        boolean resuming, int paidProgress, int previousTime) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(recipe);
        if (recipe.time() < 1 || paidProgress < 0)
            throw new IllegalArgumentException("Invalid cycle time/progress");
        if (active())
            return false;
        var next = CyclePlan.of(id, recipe, inventory, resuming).orElse(null);
        if (next == null || !next.reserve(inventory))
            return false;
        plan = next;
        progress = resuming && previousTime == next.time()
            ? Math.min(paidProgress, next.time())
            : 0;
        return true;
    }

    /**
     * Advances at most one processing tick. A completed cycle releases its
     * claims and commits its products atomically through CyclePlan. A blocked
     * completion retries without charging energy again.
     */
    public Result advance(RandomSource random) {
        Objects.requireNonNull(random);
        if (plan == null)
            return Result.IDLE;
        if (progress < plan.time()) {
            if (!draw())
                return Result.STALLED;
            progress++;
        }
        if (progress < plan.time())
            return Result.RUNNING;
        if (!plan.finish(inventory, random))
            return Result.BLOCKED;
        plan = null;
        progress = 0;
        return Result.COMPLETED;
    }

    private boolean draw() {
        for (var need : plan.energy().entrySet()) {
            long share = EnergySchedule.share(need.getValue(), plan.time(),
                progress);
            if (!(inventory.group(need.getKey())
                .orElse(null) instanceof EnergyGroup group)
                || group.stored() < share)
                return false;
        }
        try (var tx = inventory.begin()) {
            for (var need : plan.energy().entrySet()) {
                long share = EnergySchedule.share(need.getValue(), plan.time(),
                    progress);
                if (!(inventory.group(need.getKey())
                    .orElse(null) instanceof EnergyGroup group)
                    || group.consume(share, tx) < share)
                    return false;
            }
            tx.commit();
            return true;
        }
    }

    /** Release claims while retaining paid progress for an explicit resume. */
    public void release() {
        if (plan != null)
            plan.release(inventory);
        plan = null;
    }

    public void reset() {
        release();
        progress = 0;
    }

    /**
     * Loads after storage. Repeated restores never accumulate claims. Failed
     * restores release old work and leave an idle runner, preserving items.
     */
    public boolean restore(State saved) {
        Objects.requireNonNull(saved);
        reset();
        var next = saved.plan().orElse(null);
        if (next == null) {
            progress = saved.progress();
            return true;
        }
        if (next.time() < 1 || next.energy().entrySet().stream()
            .anyMatch(e -> e.getValue() < 0 || !(inventory.group(e.getKey())
                .orElse(null) instanceof EnergyGroup)))
            return false;
        if (!next.restore(inventory))
            return false;
        plan = next;
        progress = Math.min(saved.progress(), next.time());
        return true;
    }

    /** Client/paused progress without installing any server reservation. */
    void idleProgress(int value) {
        if (active() || value < 0)
            throw new IllegalStateException("Expected idle cycle progress");
        progress = value;
    }
}
