package zone.moddev.mc.lib.machine.feature;

import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import zone.moddev.mc.lib.machine.presentation.VisualVariables;

/**
 * Base for processors with observable state shared by ticks and declared
 * actions.
 */
public abstract class StoredProcessor implements Processor<CompoundTag> {
    protected final ProcessorState variables = new ProcessorState();
    private final ProcessorState.Key<Boolean> paused = variables
        .define("paused", Codec.BOOL, false);
    private final ProcessorState.Key<String> status = variables.define("status",
        Codec.STRING
            .validate(value -> java.util.Arrays.stream(ProcessState.values())
                .anyMatch(state -> state.name().equals(value))
                    ? com.mojang.serialization.DataResult.success(value)
                    : com.mojang.serialization.DataResult
                        .error(() -> "Unknown process state: " + value)),
        "IDLE");
    private final ProcessorState.Key<Boolean> held = variables.define("held",
        Codec.BOOL, false);
    private String key;
    protected ProcessorMachine machine;

    public final void initialize(String id, ProcessorMachine context) {
        if (key != null)
            throw new IllegalStateException("Processor already initialized");
        key = id;
        machine = context;
        variables.onBatch(sync -> {
            if (machine != null)
                machine.changed(sync);
        });
    }

    public abstract boolean canStart(ProcessorState state,
        ProcessorMachine context);
    public abstract ProcessState process(ProcessorState state,
        ProcessorMachine context, int deltaTicks);

    /**
     * Idle bookkeeping, including cooldowns, runs even when canStart is false.
     */
    protected void idle(ProcessorState state, ProcessorMachine context,
        int deltaTicks) {
    }

    public final String key() {
        return key;
    }
    public final Codec<CompoundTag> codec() {
        return CompoundTag.CODEC;
    }
    public final CompoundTag state() {
        return variables.save(false);
    }
    public final CompoundTag clientState() {
        return variables.save(true);
    }
    public final java.util.Map<String, Object> variableDefaults() {
        return variables.clientDefaults();
    }
    public final Object variable(String name) {
        return variables.clientValue(name);
    }
    public final boolean syncsToClient() {
        return true;
    }
    public final void restore(CompoundTag data) {
        variables.load(data, false);
    }
    public final void restoreOnClient(CompoundTag data) {
        variables.load(data, true);
    }
    public final void refreshDerived() {
        variables.refreshDerived();
    }
    public final boolean paused() {
        return variables.get(paused);
    }
    public final void setPaused(boolean value) {
        variables.update(() -> variables.set(paused, value));
    }
    public final boolean processing() {
        return !paused() && !variables.get(held)
            && variables.get(status).equals("RUNNING");
    }
    public final ProcessState executionStatus() {
        return paused()
            ? ProcessState.PAUSED
            : variables.get(held)
                ? ProcessState.HELD
                : ProcessState.valueOf(variables.get(status));
    }
    public final void defineVisualVariables(VisualVariables.Builder builder) {
        variables.defineVisualVariables(builder);
    }

    public final void tick(FeatureHost host) {
        variables.update(() -> {
            variables.set(held, false);
            if (paused())
                return;
            boolean active = !variables.get(status).equals("IDLE");
            if (!active)
                idle(variables, machine, 1);
            var next = active || canStart(variables, machine)
                ? process(variables, machine, 1)
                : ProcessState.IDLE;
            variables.set(status, next.name());
        });
    }

    public final void suspended(FeatureHost host) {
        // Keep active-operation status so resume does not incorrectly call
        // canStart again.
        variables.update(() -> variables.set(held, true));
    }

    public final boolean actionAvailable(ResourceLocation id, Player player) {
        var action = actions().get(id);
        return action != null && machine != null && !machine.removed()
            && action.available().test(variables,
                new ProcessorAction.Context(machine, player));
    }

    public final ProcessorAction.Result invoke(ResourceLocation id,
        Player player) {
        if (machine == null || machine.level() == null
            || machine.level().isClientSide() || !actionAvailable(id, player))
            return ProcessorAction.Result.unavailable();
        var result = new ProcessorAction.Result[]{
            ProcessorAction.Result.unavailable()};
        variables.update(() -> result[0] = actions().get(id).handler()
            .execute(variables, new ProcessorAction.Context(machine, player)));
        return result[0];
    }
}
