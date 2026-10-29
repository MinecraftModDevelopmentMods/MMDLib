package zone.moddev.mc.lib.common.builders;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobCategory;

import zone.moddev.mc.lib.common.types.FactoryData;
import zone.moddev.mc.lib.common.types.MMDEntity;

/**
 * Builds {@link zone.moddev.mc.lib.common.types.MMDEntity} definitions for a material's world
 * entities, such as arrow projectiles. Start from {@link #create()}; every setter returns a new
 * builder.
 */
public final class EntityBuilder extends Builder<MMDEntity, EntityBuilder> {
  /** The empty builder every chain starts from. */
  public static final EntityBuilder INSTANCE = new EntityBuilder(Map.of());

  private enum Key {
    NAME,
    MATERIAL,
    PARENT,
    FACTORY,
    CATEGORY,
    WIDTH,
    HEIGHT,
    TRACKING,
    INTERVAL,
    SAVE,
    SUMMON,
    FIRE_IMMUNE,
    DATA,
    EVENTS
  }

  private final Map<Key, Object> values;

  private EntityBuilder(Map<Key, Object> values) {
    this.values = Map.copyOf(values);
  }

  /** The empty builder. */
  public static EntityBuilder create() {
    return INSTANCE;
  }

  @SuppressWarnings("unchecked")
  private <T> T value(Key key, T fallback) {
    return (T) values.getOrDefault(key, fallback);
  }

  private EntityBuilder with(Key key, Object value) {
    var next = new EnumMap<Key, Object>(Key.class);
    next.putAll(values);
    next.put(key, Objects.requireNonNull(value, key.name()));
    return new EntityBuilder(next);
  }

  @Override
  public EntityBuilder setName(ResourceLocation name) {
    return with(Key.NAME, name);
  }

  @Override
  public EntityBuilder setName(String name) {
    return setName(ResourceLocation.parse(name));
  }

  /** The template (part) name, or null if not set. */
  public ResourceLocation getTemplateName() {
    return value(Key.NAME, null);
  }

  /** The owning material, or null if not set. */
  public ResourceLocation getMaterialName() {
    return value(Key.MATERIAL, null);
  }

  /** Binds the entity to {@code material}. */
  public EntityBuilder forMaterial(ResourceLocation material) {
    return with(Key.MATERIAL, material);
  }

  /** As {@link #forMaterial(ResourceLocation)}, parsing {@code material}. */
  public EntityBuilder forMaterial(String material) {
    return forMaterial(ResourceLocation.parse(material));
  }

  @Override
  public ResourceLocation getRegistryName() {
    var template = getTemplateName();
    var material = getMaterialName();
    return template == null || material == null
        ? null
        : ResourceLocation.fromNamespaceAndPath(
            material.getNamespace(), material.getPath() + "_" + template.getPath());
  }

  @Override
  public EntityBuilder setParent(ResourceLocation parent) {
    return with(Key.PARENT, parent);
  }

  /** Sets the ID of the entity factory that creates the entity type. */
  public EntityBuilder setFactory(ResourceLocation factory) {
    return with(Key.FACTORY, factory);
  }

  /** As {@link #setFactory(ResourceLocation)}, parsing {@code factory}. */
  public EntityBuilder setFactory(String factory) {
    return setFactory(ResourceLocation.parse(factory));
  }

  /** Sets the entity factory's settings. */
  public EntityBuilder setFactoryData(FactoryData data) {
    return with(Key.DATA, data);
  }

  /** Sets the mob category. */
  public EntityBuilder setCategory(MobCategory category) {
    return with(Key.CATEGORY, category);
  }

  /**
   * Sets the hitbox size in blocks.
   *
   * @throws IllegalArgumentException if not finite or negative
   */
  public EntityBuilder setDimensions(float width, float height) {
    MMDEntity.validateDimensions(width, height);
    return with(Key.WIDTH, width).with(Key.HEIGHT, height);
  }

  /** Sets how far, in chunks, clients track the entity. */
  public EntityBuilder setTrackingRange(int range) {
    if (range < 0) throw new IllegalArgumentException("Tracking range must not be negative");
    return with(Key.TRACKING, range);
  }

  /** Sets the ticks between position updates sent to clients. */
  public EntityBuilder setUpdateInterval(int ticks) {
    if (ticks < 1) throw new IllegalArgumentException("Update interval must be positive");
    return with(Key.INTERVAL, ticks);
  }

  /** Sets whether the entity is saved with its chunk. */
  public EntityBuilder setSave(boolean save) {
    return with(Key.SAVE, save);
  }

  /** Sets whether {@code /summon} may create the entity. */
  public EntityBuilder setSummon(boolean summon) {
    return with(Key.SUMMON, summon);
  }

  /** Sets whether fire and lava harm the entity. */
  public EntityBuilder setFireImmune(boolean immune) {
    return with(Key.FIRE_IMMUNE, immune);
  }

  @Override
  public EntityBuilder setEventMap(Map<String, ResourceLocation> events) {
    events.forEach(
        (event, action) -> {
          if (event.isBlank()) throw new IllegalArgumentException("Event name must not be blank");
          Objects.requireNonNull(action, "event action");
        });
    return with(Key.EVENTS, Map.copyOf(events));
  }

  /** Binds the entity to {@code material} and builds it. */
  public MMDEntity buildFor(ResourceLocation material) {
    return forMaterial(material).build();
  }

  @Override
  public MMDEntity build() {
    if (getTemplateName() == null) throw new IllegalStateException("Entity template needs a name");
    if (getMaterialName() == null)
      throw new IllegalStateException("Entity template must be bound to a material");
    ResourceLocation factory = value(Key.FACTORY, null);
    if (factory == null) throw new IllegalStateException("Entity template needs a factory ID");
    return new MMDEntity(
        getRegistryName(),
        getTemplateName(),
        getMaterialName(),
        value(Key.PARENT, null),
        factory,
        value(Key.CATEGORY, MobCategory.MISC),
        value(Key.WIDTH, .5F),
        value(Key.HEIGHT, .5F),
        value(Key.TRACKING, 4),
        value(Key.INTERVAL, 3),
        value(Key.SAVE, true),
        value(Key.SUMMON, true),
        value(Key.FIRE_IMMUNE, false),
        value(Key.DATA, FactoryData.EMPTY),
        value(Key.EVENTS, Map.of()));
  }
}
