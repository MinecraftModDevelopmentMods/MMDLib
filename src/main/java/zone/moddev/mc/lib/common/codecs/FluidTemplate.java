package zone.moddev.mc.lib.common.codecs;

import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.common.builders.FluidBuilder;
import zone.moddev.mc.lib.common.types.MMDFluid;

/** Common fluid-family schema; no registered fluid objects are needed for decoding. */
public record FluidTemplate(
    Optional<ResourceLocation> name,
    Optional<ResourceLocation> parent,
    MMDFluid.TypeProperties typeProperties,
    MMDFluid.FlowProperties flowProperties,
    MMDFluid.Appearance appearance,
    boolean hasBlock,
    Optional<BlockTemplate> block,
    boolean hasBucket,
    Optional<ItemTemplate> bucket,
    Map<String, ResourceLocation> events) {
  public FluidTemplate {
    events = Map.copyOf(events);
  }

  private static final Codec<FluidTemplate> FIELDS =
      RecordCodecBuilder.create(
          i ->
              i.group(
                      ResourceLocation.CODEC.optionalFieldOf("name").forGetter(FluidTemplate::name),
                      ResourceLocation.CODEC
                          .optionalFieldOf("parent")
                          .forGetter(FluidTemplate::parent),
                      MMDFluid.TypeProperties.CODEC
                          .optionalFieldOf("type_properties", MMDFluid.TypeProperties.DEFAULT)
                          .forGetter(FluidTemplate::typeProperties),
                      MMDFluid.FlowProperties.CODEC
                          .optionalFieldOf("flow_properties", MMDFluid.FlowProperties.DEFAULT)
                          .forGetter(FluidTemplate::flowProperties),
                      MMDFluid.Appearance.CODEC
                          .optionalFieldOf("appearance", MMDFluid.Appearance.DEFAULT)
                          .forGetter(FluidTemplate::appearance),
                      Codec.BOOL
                          .optionalFieldOf("has_block", true)
                          .forGetter(FluidTemplate::hasBlock),
                      BlockTemplate.CODEC.optionalFieldOf("block").forGetter(FluidTemplate::block),
                      Codec.BOOL
                          .optionalFieldOf("has_bucket", true)
                          .forGetter(FluidTemplate::hasBucket),
                      ItemTemplate.CODEC.optionalFieldOf("bucket").forGetter(FluidTemplate::bucket),
                      Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC)
                          .optionalFieldOf("events", Map.of())
                          .forGetter(FluidTemplate::events))
                  .apply(i, FluidTemplate::new));
  public static final Codec<FluidTemplate> CODEC =
      FIELDS.validate(
          value -> {
            if (!value.hasBlock && value.block.isPresent())
              return DataResult.error(() -> "Cannot combine has_block=false with block settings");
            if (!value.hasBucket && value.bucket.isPresent())
              return DataResult.error(() -> "Cannot combine has_bucket=false with bucket settings");
            if (value.block.isPresent() && value.block.get().item().isPresent())
              return DataResult.error(
                  () -> "Liquid blocks cannot declare a block item; use bucket settings");
            try {
              value.toBuilder();
              return DataResult.success(value);
            } catch (IllegalArgumentException ex) {
              return DataResult.error(ex::getMessage);
            }
          });

  /** A fluid builder holding this template's values. */
  public FluidBuilder toBuilder() {
    var builder =
        FluidBuilder.create()
            .setTypeProperties(typeProperties)
            .setFlowProperties(flowProperties)
            .setAppearance(appearance)
            .setHasBlock(hasBlock)
            .setHasBucket(hasBucket)
            .setEventMap(events);
    if (name.isPresent()) builder = builder.setName(name.get());
    if (parent.isPresent()) builder = builder.setParent(parent.get());
    if (block.isPresent()) builder = builder.setBlockTemplate(block.get().toBuilder());
    if (bucket.isPresent()) builder = builder.setBucketTemplate(bucket.get().toBuilder());
    return builder;
  }
}
