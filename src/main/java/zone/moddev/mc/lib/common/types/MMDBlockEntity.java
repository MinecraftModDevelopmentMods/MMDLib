package zone.moddev.mc.lib.common.types;

import java.util.Objects;

import net.minecraft.resources.ResourceLocation;

/** A material-bound block-entity type declaration; contains no live block or block entity. */
public record MMDBlockEntity(
    ResourceLocation name,
    ResourceLocation material,
    ResourceLocation block,
    ResourceLocation factory,
    FactoryData factoryData) {
  /** Requires every component. */
  public MMDBlockEntity {
    Objects.requireNonNull(name);
    Objects.requireNonNull(material);
    Objects.requireNonNull(block);
    Objects.requireNonNull(factory);
    Objects.requireNonNull(factoryData);
  }
}
