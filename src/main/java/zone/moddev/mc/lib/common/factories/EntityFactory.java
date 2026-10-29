package zone.moddev.mc.lib.common.factories;

import com.mojang.serialization.Codec;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import zone.moddev.mc.lib.common.types.MMDEntity;

/** Implementations must return the declared entity class, bound to the supplied type and level. */
public interface EntityFactory<E extends Entity, S> {
  Class<E> entityClass();

  Codec<S> settingsCodec();

  E create(EntityType<E> type, Level level, MMDEntity definition, S settings);

  /** Optional type-level customization after the common template properties have been applied. */
  default void configureType(EntityType.Builder<E> builder, MMDEntity definition, S settings) {}
}
