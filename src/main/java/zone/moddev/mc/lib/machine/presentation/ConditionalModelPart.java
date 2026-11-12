package zone.moddev.mc.lib.machine.presentation;

import net.minecraft.resources.ResourceLocation;

/** A static child model included when its typed condition is true. */
public record ConditionalModelPart(String id, ResourceLocation model,
    String when) {
}
