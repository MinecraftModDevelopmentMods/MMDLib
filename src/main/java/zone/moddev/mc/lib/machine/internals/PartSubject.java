package zone.moddev.mc.lib.machine.internals;

import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.common.types.MaterialProperties;

/**
 * What part files need to know about an item: which material part it is (for
 * templates) and its material's stats (for {@code $material.*}). Both are empty
 * for an item MMDLib didn't make.
 */
public record PartSubject(ResourceLocation item,
    Optional<ResourceLocation> part, Optional<MaterialProperties> material) {
}
