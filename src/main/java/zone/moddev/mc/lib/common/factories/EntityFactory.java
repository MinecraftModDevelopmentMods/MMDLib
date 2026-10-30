package zone.moddev.mc.lib.common.factories;

import com.mojang.serialization.Codec;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import zone.moddev.mc.lib.common.types.MMDEntity;

/**
 * The Java side of a data-defined world entity: one implementation, registered once in an {@link
 * EntityFactories} catalog, serves every entity definition that names its ID. Each definition
 * carries its own settings, decoded with {@link #settingsCodec()} when definitions load, so several
 * data variants can share one implementation.
 *
 * <p>Implementations should be stateless; per-entity state belongs in the entity, and per-variant
 * configuration in the settings.
 *
 * @param <E> the entity class this factory creates
 * @param <S> the decoded settings type, shared by every entity of one definition
 */
public interface EntityFactory<E extends Entity, S> {
  /**
   * The exact class {@link #create} returns. {@link EntityFactories#prepare} refuses a caller that
   * expects a different class, and every created entity is cast to it.
   *
   * @return the entity class; never null
   */
  Class<E> entityClass();

  /**
   * Decodes a definition's {@code factory_data} into settings. It runs when definitions load,
   * before any registry exists, so invalid data fails at startup naming the definition. Settings
   * that refer to registry contents can be decoded through {@code RegistryOps} with {@link
   * EntityFactories#prepare(MMDEntity, Class, com.mojang.serialization.DynamicOps)}.
   *
   * @return the settings codec; never null
   */
  Codec<S> settingsCodec();

  /**
   * Creates an entity. Minecraft calls this through the entity type whenever an entity of this
   * definition is spawned, summoned or loaded, on either logical side. The result must be
   * constructed with exactly the given {@code type} and {@code level}; the type's constructor
   * checks this and throws if it was not.
   *
   * @param type the registered entity type built for {@code definition}
   * @param level the level the entity is created in
   * @param definition the entity definition being created
   * @param settings the settings decoded from {@code definition}
   * @return a new entity; never null
   */
  E create(EntityType<E> type, Level level, MMDEntity definition, S settings);

  /**
   * Adjusts the entity type as it is built, after the definition's size, tracking range, update
   * interval and save, summon and fire-immunity flags have been applied, so changes here win. Runs
   * once, from the entity-type registry supplier. The default changes nothing.
   *
   * @param builder the type builder, already configured from {@code definition}
   * @param definition the entity definition whose type is being built
   * @param settings the settings decoded from {@code definition}
   */
  default void configureType(EntityType.Builder<E> builder, MMDEntity definition, S settings) {}
}
