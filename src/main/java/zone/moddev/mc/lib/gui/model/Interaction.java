package zone.moddev.mc.lib.gui.model;

import net.minecraft.resources.ResourceLocation;

/**
 * A reusable server interaction and its screen-authored target, never supplied by a click packet.
 */
public record Interaction(ResourceLocation type, String target) {
    public Interaction {
        java.util.Objects.requireNonNull(type);
        java.util.Objects.requireNonNull(target);
    }
}
