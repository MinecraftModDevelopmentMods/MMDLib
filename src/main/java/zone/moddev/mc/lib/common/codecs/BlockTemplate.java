package zone.moddev.mc.lib.common.codecs;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.common.builders.BlockBuilder;
import zone.moddev.mc.lib.common.types.MMDBlock;

/** Data-only block schema; does not create or register game objects. */
public record BlockTemplate(
    Optional<ResourceLocation> name,
    Optional<ResourceLocation> parent,
    String type,
    MMDBlock.Properties properties,
    Set<ResourceLocation> tags,
    Map<String, ResourceLocation> events,
    boolean hasItem,
    Optional<ItemTemplate> item) {
  /** Copies the tags and event bindings. */
  public BlockTemplate {
    tags = Set.copyOf(tags);
    events = Map.copyOf(events);
  }

  private static final Codec<Set<ResourceLocation>> TAGS =
      ResourceLocation.CODEC.listOf().xmap(Set::copyOf, tags -> tags.stream().sorted().toList());
  private static final Codec<BlockTemplate> FIELDS =
      RecordCodecBuilder.create(
          i ->
              i.group(
                      ResourceLocation.CODEC.optionalFieldOf("name").forGetter(BlockTemplate::name),
                      ResourceLocation.CODEC
                          .optionalFieldOf("parent")
                          .forGetter(BlockTemplate::parent),
                      Codec.STRING.optionalFieldOf("type", "block").forGetter(BlockTemplate::type),
                      MMDBlock.Properties.CODEC
                          .optionalFieldOf("properties", MMDBlock.Properties.DEFAULT)
                          .forGetter(BlockTemplate::properties),
                      TAGS.optionalFieldOf("tags", Set.of()).forGetter(BlockTemplate::tags),
                      Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC)
                          .optionalFieldOf("events", Map.of())
                          .forGetter(BlockTemplate::events),
                      Codec.BOOL
                          .optionalFieldOf("has_item", true)
                          .forGetter(BlockTemplate::hasItem),
                      ItemTemplate.CODEC.optionalFieldOf("item").forGetter(BlockTemplate::item))
                  .apply(i, BlockTemplate::new));
  public static final Codec<BlockTemplate> CODEC =
      FIELDS.validate(
          template -> {
            if (!template.hasItem && template.item.isPresent())
              return DataResult.error(() -> "Cannot combine has_item=false with an item template");
            try {
              template.toBuilder();
              return DataResult.success(template);
            } catch (IllegalArgumentException ex) {
              return DataResult.error(ex::getMessage);
            }
          });

  /** A block builder holding this template's values. */
  public BlockBuilder toBuilder() {
    BlockBuilder b =
        BlockBuilder.create()
            .setType(type)
            .setProperties(properties)
            .setBlockTags(tags)
            .setEventMap(events)
            .setHasItem(hasItem);
    if (name.isPresent()) b = b.setName(name.get());
    if (parent.isPresent()) b = b.setParent(parent.get());
    if (item.isPresent()) b = b.setItemTemplate(item.get().toBuilder());
    return b;
  }
}
