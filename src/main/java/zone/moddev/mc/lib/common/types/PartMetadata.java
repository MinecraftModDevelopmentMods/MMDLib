package zone.moddev.mc.lib.common.types;

import java.util.*;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.common.codecs.BlockEntityTemplate;

/** Data-owned aliases and companions. Behavior settings belong to the selected factory's codec. */
public record PartMetadata(
    List<String> aliases,
    Optional<ResourceLocation> entityFactory,
    FactoryData entityData,
    Optional<BlockEntityTemplate> blockEntity) {
  public static final PartMetadata EMPTY =
      new PartMetadata(List.of(), Optional.empty(), FactoryData.EMPTY, Optional.empty());
  public static final Codec<PartMetadata> CODEC =
      RecordCodecBuilder.<PartMetadata>create(
              i ->
                  i.group(
                          Codec.STRING
                              .listOf()
                              .optionalFieldOf("aliases", List.of())
                              .forGetter(PartMetadata::aliases),
                          ResourceLocation.CODEC
                              .optionalFieldOf("entity_factory")
                              .forGetter(PartMetadata::entityFactory),
                          FactoryData.CODEC
                              .optionalFieldOf("entity_data", FactoryData.EMPTY)
                              .forGetter(PartMetadata::entityData),
                          BlockEntityTemplate.CODEC
                              .optionalFieldOf("block_entity")
                              .forGetter(PartMetadata::blockEntity))
                      .apply(i, PartMetadata::new))
          .validate(
              value ->
                  value.entityFactory().isEmpty() && !value.entityData().equals(FactoryData.EMPTY)
                      ? com.mojang.serialization.DataResult.error(
                          () -> "entity_data requires entity_factory")
                      : com.mojang.serialization.DataResult.success(value));

  public PartMetadata {
    aliases = List.copyOf(aliases);
    Objects.requireNonNull(entityFactory);
    Objects.requireNonNull(entityData);
    Objects.requireNonNull(blockEntity);
  }
}
