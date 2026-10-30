package zone.moddev.mc.lib.common.codecs;

import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobCategory;

import zone.moddev.mc.lib.common.builders.EntityBuilder;
import zone.moddev.mc.lib.common.types.FactoryData;

/**
 * The JSON form of a world-entity definition, before it is bound to a material. Fields match {@link
 * zone.moddev.mc.lib.common.types.MMDEntity}.
 */
public record EntityTemplate(
    Optional<ResourceLocation> name,
    Optional<ResourceLocation> parent,
    ResourceLocation factory,
    MobCategory category,
    float width,
    float height,
    int trackingRange,
    int updateInterval,
    boolean save,
    boolean summon,
    boolean fireImmune,
    FactoryData factoryData,
    Map<String, ResourceLocation> events) {
  /** Copies the event bindings. */
  public EntityTemplate {
    events = Map.copyOf(events);
  }

  private static final Codec<Float> DIMENSION =
      Codec.FLOAT.validate(
          value ->
              Float.isFinite(value) && value >= 0
                  ? DataResult.success(value)
                  : DataResult.error(() -> "Entity dimensions must be finite and nonnegative"));
  private static final Codec<EntityTemplate> FIELDS =
      RecordCodecBuilder.create(
          i ->
              i.group(
                      ResourceLocation.CODEC
                          .optionalFieldOf("name")
                          .forGetter(EntityTemplate::name),
                      ResourceLocation.CODEC
                          .optionalFieldOf("parent")
                          .forGetter(EntityTemplate::parent),
                      ResourceLocation.CODEC.fieldOf("factory").forGetter(EntityTemplate::factory),
                      MobCategory.CODEC
                          .optionalFieldOf("category", MobCategory.MISC)
                          .forGetter(EntityTemplate::category),
                      DIMENSION.optionalFieldOf("width", .5F).forGetter(EntityTemplate::width),
                      DIMENSION.optionalFieldOf("height", .5F).forGetter(EntityTemplate::height),
                      Codec.intRange(0, Integer.MAX_VALUE)
                          .optionalFieldOf("tracking_range", 4)
                          .forGetter(EntityTemplate::trackingRange),
                      Codec.intRange(1, Integer.MAX_VALUE)
                          .optionalFieldOf("update_interval", 3)
                          .forGetter(EntityTemplate::updateInterval),
                      Codec.BOOL.optionalFieldOf("save", true).forGetter(EntityTemplate::save),
                      Codec.BOOL.optionalFieldOf("summon", true).forGetter(EntityTemplate::summon),
                      Codec.BOOL
                          .optionalFieldOf("fire_immune", false)
                          .forGetter(EntityTemplate::fireImmune),
                      FactoryData.CODEC
                          .optionalFieldOf("factory_data", FactoryData.EMPTY)
                          .forGetter(EntityTemplate::factoryData),
                      Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC)
                          .optionalFieldOf("events", Map.of())
                          .forGetter(EntityTemplate::events))
                  .apply(i, EntityTemplate::new));
  public static final Codec<EntityTemplate> CODEC =
      FIELDS.validate(
          value -> {
            try {
              value.toBuilder();
              return DataResult.success(value);
            } catch (IllegalArgumentException ex) {
              return DataResult.error(ex::getMessage);
            }
          });

  /** An entity builder holding this template's values. */
  public EntityBuilder toBuilder() {
    var b =
        EntityBuilder.create()
            .setFactory(factory)
            .setCategory(category)
            .setDimensions(width, height)
            .setTrackingRange(trackingRange)
            .setUpdateInterval(updateInterval)
            .setSave(save)
            .setSummon(summon)
            .setFireImmune(fireImmune)
            .setFactoryData(factoryData)
            .setEventMap(events);
    if (name.isPresent()) b = b.setName(name.get());
    if (parent.isPresent()) b = b.setParent(parent.get());
    return b;
  }
}
