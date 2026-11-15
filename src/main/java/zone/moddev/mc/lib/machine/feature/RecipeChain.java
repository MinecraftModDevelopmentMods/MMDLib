package zone.moddev.mc.lib.machine.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.machine.ingredient.MachineIngredient.ItemPart;
import zone.moddev.mc.lib.machine.inventory.ItemGroup;
import zone.moddev.mc.lib.machine.inventory.MachineInventory;
import zone.moddev.mc.lib.machine.recipe.MachineProduct.ItemProduct;
import zone.moddev.mc.lib.machine.recipe.MachineRecipe;

/**
 * Bounded concurrent execution of a selected linear recipe path. The host owns
 * selection, storage, saving and synchronization. All methods run on its server
 * thread; quantity queries are pure and safe during extraction simulation. Item
 * groups must be exclusive to this chain. Recipes use "input"/"output" for
 * stage items, with other names available for fluids and shared energy.
 */
public final class RecipeChain {
    public static final int MAX_STAGES = 64;

    public record Step(ResourceLocation id, MachineRecipe recipe,
        MachineInventory inventory) {
        public Step {
            Objects.requireNonNull(id);
            Objects.requireNonNull(recipe);
            Objects.requireNonNull(inventory);
            var products = recipe.outputs().get("output");
            if (recipe.time() < 1 || recipe.outputs().size() != 1
                || products == null || products.size() != 1
                || !(products.getFirst() instanceof ItemProduct item)
                || item.chance() != 1 || item.stack().isEmpty()
                || item.stack().getCount() > item.stack().getMaxStackSize()
                || !(inventory.group("output")
                    .orElse(null) instanceof ItemGroup output)
                || output.size() != 1)
                throw new IllegalArgumentException(
                    "Chain steps require one deterministic item output slot");
            var input = recipe.inputs().getOrDefault("input", List.of());
            if (!input.isEmpty() && (input.size() != 1
                || !(input.getFirst() instanceof ItemPart)))
                throw new IllegalArgumentException(
                    "The input group is for stage items only");
            for (var entry : recipe.inputs().entrySet())
                for (var part : entry.getValue())
                    if (part instanceof ItemPart
                        && (!entry.getKey().equals("input")
                            || entry.getValue().size() != 1 || !part.consumed()
                            || part.amount() < 1))
                        throw new IllegalArgumentException(
                            "Chain steps consume one item ingredient group");
        }

        public ItemStack product() {
            return ((ItemProduct) recipe.outputs().get("output").getFirst())
                .stack();
        }

        public ItemGroup output() {
            return (ItemGroup) inventory.group("output").orElseThrow();
        }

        public int inputCount() {
            return recipe.inputs().getOrDefault("input", List.of()).stream()
                .filter(ItemPart.class::isInstance).map(ItemPart.class::cast)
                .mapToInt(ItemPart::count).sum();
        }
    }

    public record StageState(ResourceLocation recipe, int target,
        RecipeCycle.State cycle) {
        public static final Codec<StageState> CODEC = RecordCodecBuilder
            .create(
                i -> i
                    .group(
                        ResourceLocation.CODEC.fieldOf("recipe")
                            .forGetter(StageState::recipe),
                        Codec.intRange(0, Integer.MAX_VALUE).fieldOf("target")
                            .forGetter(StageState::target),
                        RecipeCycle.State.CODEC.fieldOf("cycle")
                            .forGetter(StageState::cycle))
                    .apply(i, StageState::new));
    }

    public record State(List<StageState> stages, boolean paused,
        boolean draining, int first) {
        public static final Codec<State> CODEC = RecordCodecBuilder
            .create(i -> i.group(
                StageState.CODEC.listOf(1, MAX_STAGES).fieldOf("stages")
                    .forGetter(State::stages),
                Codec.BOOL.optionalFieldOf("paused", false)
                    .forGetter(State::paused),
                Codec.BOOL.optionalFieldOf("draining", false)
                    .forGetter(State::draining),
                Codec.intRange(0, MAX_STAGES - 1).optionalFieldOf("first", 0)
                    .forGetter(State::first))
                .apply(i, State::new));

        public State {
            stages = List.copyOf(stages);
        }
    }

    private final List<Step> steps;
    private final List<RecipeCycle> cycles;
    private final int[] targets;
    private final int[] bufferLimits;
    private final RecipeCycle.Result[] results;
    private boolean paused;
    private boolean held;
    private boolean draining;
    private int first;

    public RecipeChain(List<Step> steps) {
        if (steps.isEmpty() || steps.size() > MAX_STAGES)
            throw new IllegalArgumentException("Invalid chain length");
        this.steps = List.copyOf(steps);
        var outputs = java.util.Collections
            .newSetFromMap(new java.util.IdentityHashMap<ItemGroup, Boolean>());
        for (int i = 0; i < steps.size(); i++) {
            var step = steps.get(i);
            if (!outputs.add(step.output()))
                throw new IllegalArgumentException("Aliased stage outputs");
            if (i == 0 && step.inputCount() != 0)
                throw new IllegalArgumentException(
                    "Root must generate its item");
            if (i > 0) {
                var previous = steps.get(i - 1);
                if (step.inputCount() == 0
                    || step.inputCount() > previous.product().getMaxStackSize()
                    || step.inventory().group("input").orElse(null) != previous
                        .output()
                    || !((ItemPart) step.recipe().inputs().get("input")
                        .getFirst()).items().test(previous.product()))
                    throw new IllegalArgumentException(
                        "Each input must be the preceding output group");
            }
        }
        cycles = steps.stream().map(s -> new RecipeCycle(s.inventory()))
            .toList();
        targets = new int[steps.size()];
        bufferLimits = steps.stream()
            .mapToInt(s -> s.product().getMaxStackSize()).toArray();
        results = new RecipeCycle.Result[steps.size()];
        java.util.Arrays.fill(results, RecipeCycle.Result.IDLE);
    }

    public int size() {
        return steps.size();
    }

    public Step step(int index) {
        return steps.get(index);
    }

    public RecipeCycle.State cycle(int index) {
        return cycles.get(index).state();
    }

    public RecipeCycle.Result result(int index) {
        return results[index];
    }

    public int target(int index) {
        return targets[index];
    }

    /** Numeric policy; a consumer maps its None/One/Stack controls here. */
    public void target(int index, int amount) {
        if (amount < 0 || amount > steps.get(index).product().getMaxStackSize())
            throw new IllegalArgumentException(
                "Target outside one normal stack");
        targets[index] = amount;
    }

    /**
     * Host-owned buffer policy, reapplied when binding/restoring a chain.
     * Limits future admissions, not existing stock or active work. Full output
     * batches can exceed this threshold. Allow at least the following step's
     * input batch to avoid starving that step. Defaults to a normal stack.
     */
    public void bufferLimit(int index, int amount) {
        int minimum = index + 1 < size()
            ? steps.get(index + 1).inputCount()
            : 1;
        if (amount < minimum
            || amount > steps.get(index).product().getMaxStackSize())
            throw new IllegalArgumentException(
                "Buffer limit must fit the next input batch and one normal stack");
        bufferLimits[index] = amount;
    }

    public boolean paused() {
        return paused;
    }

    /** Pause keeps active work intact, releasing only pending batch claims. */
    public void paused(boolean value) {
        paused = value;
    }

    /** Redstone/host hold, not persisted as a player's pause preference. */
    public void held(boolean value) {
        held = value;
    }

    /** Stops all admissions; existing cycles finish normally when powered. */
    public void drain() {
        draining = true;
    }

    public boolean draining() {
        return draining;
    }

    public void cancelDrain() {
        draining = false;
    }

    public boolean drained() {
        return draining && cycles.stream().noneMatch(RecipeCycle::active);
    }

    /**
     * Tick once per world tick. Advance only cycles active at entry, then admit
     * new work. Rotate the first advancing stage after a funded pass. An energy
     * stall keeps that stage first and ends the pass, allowing its next tick's
     * charge to accumulate instead of being spent by cheaper work. Returns
     * whether state changed; stock/extraction can change separately.
     */
    public boolean tick(RandomSource random) {
        return tick(random, stage -> true);
    }

    /**
     * Host-owned start conditions, evaluated immediately before each admission
     * after active cycles advance. Rejecting a stage never stops its active
     * work or prevents other stages from starting.
     */
    public boolean tick(RandomSource random,
        java.util.function.IntPredicate canStart) {
        Objects.requireNonNull(random);
        Objects.requireNonNull(canStart);
        if (paused || held)
            return false;
        boolean changed = false;
        int nextFirst = cycles.stream().anyMatch(RecipeCycle::active)
            ? (first + 1) % size()
            : first;
        for (int offset = 0; offset < size(); offset++) {
            int i = (first + offset) % size();
            var cycle = cycles.get(i);
            int before = cycle.progress();
            var result = cycle.advance(random);
            changed |= results[i] != result || before != cycle.progress()
                || result == RecipeCycle.Result.COMPLETED;
            results[i] = result;
            if (result == RecipeCycle.Result.STALLED) {
                nextFirst = i;
                break;
            }
        }
        changed |= first != nextFirst;
        first = nextFirst;
        if (!draining)
            for (int i = size() - 1; i >= 0; i--) {
                var step = steps.get(i);
                if (!cycles.get(i).active() && needed()[i] && matchingInput(i)
                    && canStart.test(i)
                    && cycles.get(i).start(step.id(), step.recipe())) {
                    results[i] = RecipeCycle.Result.RUNNING;
                    changed = true;
                }
            }
        // first is part of saved scheduling state even when energy stalled.
        return changed || cycles.stream().anyMatch(RecipeCycle::active);
    }

    /**
     * Extra claim beyond active reservations, for the next demanded batch. Bind
     * this pure projection to each physical output slot's pendingClaims. It
     * sees current contents even if an extractor ticks before this machine.
     */
    public int pendingTake(int stage) {
        steps.get(stage);
        int next = stage + 1;
        if (paused || held || draining || next == size()
            || cycles.get(next).active() || !needed()[next]
            || !matchingInput(next))
            return 0;
        var step = steps.get(next);
        if (!step.output().fits(List.of(step.product())))
            return 0;
        return Math.min(step.inputCount(), steps.get(stage).output().free(0));
    }

    private boolean[] needed() {
        var needs = new boolean[size()];
        long upstream = 0;
        for (int i = size() - 1; i >= 0; i--) {
            var step = steps.get(i);
            var held = step.output().peek(0);
            long available = ItemStack.isSameItemSameComponents(held,
                step.product()) ? held.getCount() : 0;
            if (cycles.get(i).active())
                available += plannedOutput(i);
            if (i + 1 < size() && cycles.get(i + 1).active())
                available -= plannedInput(i + 1);
            long missing = Math.max(0,
                Math.min(bufferLimits[i], Math.max(targets[i], upstream))
                    - available);
            needs[i] = missing > 0;
            long batches = (missing + step.product().getCount() - 1)
                / step.product().getCount();
            // A blocked output cannot consume more inputs. Independent upstream
            // targets still refill, subject to their own buffer limits.
            upstream = missing > 0
                && step.output().fits(List.of(step.product()))
                    ? batches * step.inputCount()
                    : 0;
        }
        return needs;
    }

    private boolean matchingInput(int stage) {
        if (stage == 0)
            return true;
        var previous = steps.get(stage - 1);
        return ItemStack.isSameItemSameComponents(previous.output().peek(0),
            previous.product());
    }

    private long plannedOutput(int stage) {
        return cycles.get(stage).plan().stream()
            .flatMap(p -> p.products().stream())
            .filter(p -> p.group().equals("output"))
            .flatMap(p -> p.at().stream()).mapToLong(a -> a.amount()).sum();
    }

    private long plannedInput(int stage) {
        return cycles.get(stage).plan().stream()
            .flatMap(p -> p.items().stream())
            .filter(t -> t.group().equals("input"))
            .mapToLong(t -> t.stack().getCount() - t.retained()).sum();
    }

    public State state() {
        var saved = new ArrayList<StageState>();
        for (int i = 0; i < size(); i++)
            saved.add(new StageState(steps.get(i).id(), targets[i],
                cycles.get(i).state()));
        return new State(saved, paused, draining, first);
    }

    /**
     * Release ownership before storage reload/destruction, never for path
     * switching.
     */
    public void reset() {
        cycles.forEach(RecipeCycle::reset);
        java.util.Arrays.fill(results, RecipeCycle.Result.IDLE);
    }

    /**
     * Restore only into the same storage bindings, after loading their items.
     * Returns false if any active plan could not be restored. No lost plan
     * consumes items; the host must retain/recover incompatible path contents.
     */
    public boolean restore(State saved) {
        if (saved.stages().size() != size() || saved.first() < 0
            || saved.first() >= size())
            throw new IllegalArgumentException("Saved chain shape differs");
        for (int i = 0; i < size(); i++) {
            var stage = saved.stages().get(i);
            if (!stage.recipe().equals(steps.get(i).id()) || stage.target() < 0
                || stage.target() > steps.get(i).product().getMaxStackSize()
                || stage.cycle().plan()
                    .filter(p -> !p.recipe().equals(stage.recipe()))
                    .isPresent())
                throw new IllegalArgumentException("Saved chain route differs");
        }
        cycles.forEach(RecipeCycle::reset);
        boolean restored = true;
        for (int i = 0; i < size(); i++) {
            var stage = saved.stages().get(i);
            targets[i] = stage.target();
            restored &= cycles.get(i).restore(stage.cycle());
            results[i] = cycles.get(i).active()
                ? RecipeCycle.Result.RUNNING
                : RecipeCycle.Result.IDLE;
        }
        paused = saved.paused();
        draining = saved.draining();
        first = saved.first();
        return restored;
    }
}
