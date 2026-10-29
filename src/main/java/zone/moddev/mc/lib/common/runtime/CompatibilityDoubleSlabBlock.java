package zone.moddev.mc.lib.common.runtime;

import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Kept solely so 1.12 double-slab states survive registry remapping. */
public final class CompatibilityDoubleSlabBlock extends SlabBlock {
  /** A slab block registered under an old double-slab ID. */
  public CompatibilityDoubleSlabBlock(Properties properties) {
    super(properties);
    registerDefaultState(
        defaultBlockState().setValue(TYPE, SlabType.DOUBLE).setValue(WATERLOGGED, false));
  }
}
