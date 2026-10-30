package zone.moddev.mc.lib.common.factories;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.google.gson.JsonElement;

import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import zone.moddev.mc.lib.common.types.MMDEntity;

/**
 * A catalog of {@link EntityFactory} implementations, keyed by the IDs entity definitions name
 * (e.g. a part's {@code entity_factory}).
 */
public final class EntityFactories {
  private final ConcurrentHashMap<ResourceLocation, EntityFactory<?, ?>> factories =
      new ConcurrentHashMap<>();

  /** An empty catalog. */
  public EntityFactories() {}

  /**
   * Registers a factory under {@code id}.
   *
   * @throws IllegalArgumentException if the ID is taken
   */
  public <E extends Entity, S> void register(ResourceLocation id, EntityFactory<E, S> factory) {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(factory, "factory");
    Objects.requireNonNull(factory.entityClass(), "entityClass");
    Objects.requireNonNull(factory.settingsCodec(), "settingsCodec");
    if (factories.putIfAbsent(id, factory) != null)
      throw new IllegalArgumentException("Entity factory already registered: " + id);
  }

  /**
   * Looks up the factory a definition names and decodes its settings now, so bad data fails at
   * load.
   *
   * @param expectedClass the entity class the caller expects; must match the factory's
   * @throws IllegalArgumentException if the factory is unknown, its class differs, or the settings
   *     are invalid
   */
  public <E extends Entity> Prepared<E> prepare(MMDEntity definition, Class<E> expectedClass) {
    return prepare(definition, expectedClass, JsonOps.INSTANCE);
  }

  /**
   * As {@link #prepare(MMDEntity, Class)}, decoding the settings with {@code ops}. Pass {@code
   * RegistryOps} when the factory's settings refer to registry contents.
   *
   * @param expectedClass the entity class the caller expects; must match the factory's
   * @param ops the ops the settings are decoded with
   * @throws IllegalArgumentException if the factory is unknown, its class differs, or the settings
   *     are invalid
   */
  public <E extends Entity> Prepared<E> prepare(
      MMDEntity definition, Class<E> expectedClass, DynamicOps<JsonElement> ops) {
    var factory = factories.get(definition.factory());
    if (factory == null)
      throw new IllegalArgumentException("Unknown entity factory: " + definition.factory());
    return prepareCaptured(definition, checked(factory, expectedClass), ops);
  }

  @SuppressWarnings("unchecked")
  private static <E extends Entity> EntityFactory<E, ?> checked(
      EntityFactory<?, ?> factory, Class<E> expectedClass) {
    if (!Objects.requireNonNull(expectedClass).equals(factory.entityClass()))
      throw new IllegalArgumentException(
          "Entity factory produces "
              + factory.entityClass().getName()
              + ", requested "
              + expectedClass.getName());
    return (EntityFactory<E, ?>) factory;
  }

  private static <E extends Entity, S> Prepared<E> prepareCaptured(
      MMDEntity definition, EntityFactory<E, S> factory, DynamicOps<JsonElement> ops) {
    S settings =
        definition
            .factoryData()
            .decode(factory.settingsCodec(), ops)
            .getOrThrow(
                message ->
                    new IllegalArgumentException(
                        definition.name() + " factory " + definition.factory() + ": " + message));
    Class<E> entityClass = factory.entityClass();
    EntityType.EntityFactory<E> constructor =
        (type, level) -> {
          E entity =
              entityClass.cast(
                  Objects.requireNonNull(
                      factory.create(type, level, definition, settings),
                      "Entity factory returned null"));
          if (entity.getType() != type || entity.level() != level)
            throw new IllegalStateException(
                "Entity factory must use the supplied type and level: " + definition.factory());
          return entity;
        };
    return new Prepared<>(
        definition,
        entityClass,
        constructor,
        builder -> factory.configureType(builder, definition, settings));
  }

  /**
   * An entity factory with its decoded settings. The settings were validated when it was prepared;
   * the type is built by {@link #buildType} only when the consuming mod's registry supplier runs.
   *
   * @param <E> the entity class
   */
  public static final class Prepared<E extends Entity> {
    private final MMDEntity definition;
    private final Class<E> entityClass;
    private final EntityType.EntityFactory<E> constructor;
    private final Consumer<EntityType.Builder<E>> configure;

    private Prepared(
        MMDEntity definition,
        Class<E> entityClass,
        EntityType.EntityFactory<E> constructor,
        Consumer<EntityType.Builder<E>> configure) {
      this.definition = definition;
      this.entityClass = entityClass;
      this.constructor = constructor;
      this.configure = configure;
    }

    /** The entity definition. */
    public MMDEntity definition() {
      return definition;
    }

    /** The class every created entity has. */
    public Class<E> entityClass() {
      return entityClass;
    }

    /**
     * Builds the entity type from the definition's size, tracking and flags, then the factory's own
     * changes. Call from the entity-type registry supplier.
     */
    public EntityType<E> buildType() {
      var builder =
          EntityType.Builder.of(constructor, definition.category())
              .sized(definition.width(), definition.height())
              .clientTrackingRange(definition.trackingRange())
              .updateInterval(definition.updateInterval());
      if (!definition.save()) builder.noSave();
      if (!definition.summon()) builder.noSummon();
      if (definition.fireImmune()) builder.fireImmune();
      configure.accept(builder);
      return builder.build(definition.name().toString());
    }
  }
}
