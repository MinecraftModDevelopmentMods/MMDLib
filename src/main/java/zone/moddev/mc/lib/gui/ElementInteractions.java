package zone.moddev.mc.lib.gui;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.model.elements.FluidTankElement;
import zone.moddev.mc.lib.gui.runtime.ScreenClick;

/**
 * Reusable element behaviors. Register during mod construction, before screens
 * load.
 */
public final class ElementInteractions {
    public interface Type {
        /** Reject invalid screen-authored targets during loading. */
        void validate(String target);

        /**
         * Runs only after the menu validates the player, distance and element
         * visibility. Return true to consume the click; false allows its
         * ordinary callback to run.
         */
        boolean click(ScreenClick click, String target);

        /** Optional resource identity for validation and highlights. */
        default java.util.Optional<zone.moddev.mc.lib.gui.model.ResourceBinding> resourceBinding(
            String target) {
            return java.util.Optional.empty();
        }
    }

    private static final Map<ResourceLocation, Type> TYPES = new HashMap<>();

    static {
        register(FluidTankElement.INTERACTION_ID,
            FluidTankElement.containerInteractionType());
    }

    private ElementInteractions() {
    }

    public static synchronized void register(ResourceLocation id, Type type) {
        java.util.Objects.requireNonNull(id);
        java.util.Objects.requireNonNull(type);
        if (TYPES.putIfAbsent(id, type) != null)
            throw new IllegalArgumentException(
                "Duplicate element interaction: " + id);
    }

    public static synchronized Type require(ResourceLocation id) {
        var type = TYPES.get(id);
        if (type == null)
            throw new IllegalArgumentException(
                "Unknown element interaction: " + id);
        return type;
    }
}
