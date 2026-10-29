package zone.moddev.mc.lib.common.factories;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;

import zone.moddev.mc.lib.common.types.MMDBlockEntity;

/** Register executable machine behavior once; modules supply its typed settings. */
public interface BlockEntityFactory<E extends BlockEntity, S> {
  Class<E> blockEntityClass();

  Codec<S> settingsCodec();

  E create(
      BlockEntityType<E> type,
      BlockPos position,
      BlockState state,
      MMDBlockEntity definition,
      S settings);

  /** Return null for a non-ticking machine or on the side where it must not tick. */
  default BlockEntityTicker<E> ticker(Level level, MMDBlockEntity definition, S settings) {
    return null;
  }
}
