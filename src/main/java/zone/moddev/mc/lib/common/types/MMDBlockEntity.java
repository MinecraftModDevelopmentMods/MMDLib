package zone.moddev.mc.lib.common.types;

import net.minecraft.resources.ResourceLocation;
import java.util.Objects;

/** A material-bound block-entity type declaration; contains no live block or block entity. */
public record MMDBlockEntity(ResourceLocation name, ResourceLocation material, ResourceLocation block,
        ResourceLocation factory, FactoryData factoryData) {
    public MMDBlockEntity {
        Objects.requireNonNull(name); Objects.requireNonNull(material); Objects.requireNonNull(block);
        Objects.requireNonNull(factory); Objects.requireNonNull(factoryData);
    }
}
