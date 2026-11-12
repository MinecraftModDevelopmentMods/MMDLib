package zone.moddev.mc.lib.machine.presentation;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.api.capabilities.RelativeFace;

/** An ordered texture override for one machine-relative face of the base model. */
public record ConditionalFaceTexture(String id, RelativeFace face, ResourceLocation texture,
    String when, ResourceLocation emissiveMask) {
    public ConditionalFaceTexture(String id, RelativeFace face, ResourceLocation texture,
        String when) {
        this(id, face, texture, when, null);
    }
}
