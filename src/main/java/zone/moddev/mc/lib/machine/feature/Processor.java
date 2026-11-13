package zone.moddev.mc.lib.machine.feature;

import java.util.Map;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import zone.moddev.mc.lib.machine.presentation.VisualVariableProvider;

/**
 * Common lifecycle and controls for recipe and world-interaction processors.
 */
public interface Processor<S>
    extends MachineFeature<S>, VisualVariableProvider {
    enum ProcessState {
        IDLE, RUNNING, STALLED, BLOCKED, HELD, PAUSED
    }

    boolean paused();
    void setPaused(boolean paused);
    boolean processing();
    ProcessState executionStatus();

    /**
     * Declared client-visible stored values; payloads are defensively copied.
     */
    default Map<String, Object> variableDefaults() {
        return Map.of();
    }

    default Object variable(String name) {
        throw new IllegalArgumentException(
            "Unknown processor variable: " + name);
    }

    /**
     * Static schema metadata, without reading or mutating item-side processor
     * state.
     */
    default java.util.Optional<PortableVariable<?>> portableVariable(
        String name) {
        return java.util.Optional.empty();
    }

    default Map<ResourceLocation, ProcessorAction> actions() {
        return Map.of();
    }

    /** Actions run on the server, including while processing is paused. */
    default ProcessorAction.Result invoke(ResourceLocation id, Player player) {
        return ProcessorAction.Result.unavailable();
    }

    default boolean actionAvailable(ResourceLocation id, Player player) {
        return false;
    }

    default Component displayName() {
        return Component.literal(key());
    }
}
