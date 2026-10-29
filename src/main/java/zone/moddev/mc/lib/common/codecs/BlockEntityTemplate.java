package zone.moddev.mc.lib.common.codecs;

import java.util.Objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.common.types.*;

/**
 * A block-owned block entity, distinct from a world Entity. Settings are decoded by its factory.
 */
public record BlockEntityTemplate(ResourceLocation factory, FactoryData factoryData) {
  public static final Codec<BlockEntityTemplate> CODEC =
      RecordCodecBuilder.create(
          i ->
              i.group(
                      ResourceLocation.CODEC
                          .fieldOf("factory")
                          .forGetter(BlockEntityTemplate::factory),
                      FactoryData.CODEC
                          .optionalFieldOf("factory_data", FactoryData.EMPTY)
                          .forGetter(BlockEntityTemplate::factoryData))
                  .apply(i, BlockEntityTemplate::new));

  public BlockEntityTemplate {
    Objects.requireNonNull(factory);
    Objects.requireNonNull(factoryData);
  }

  /**
   * The block-entity definition for a planned block: same namespace and material, named after the
   * block.
   */
  public MMDBlockEntity bind(MMDBlock block) {
    return new MMDBlockEntity(block.name(), block.material(), block.name(), factory, factoryData);
  }
}
