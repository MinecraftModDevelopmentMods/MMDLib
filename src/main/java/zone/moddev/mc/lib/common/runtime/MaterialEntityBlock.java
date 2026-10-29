package zone.moddev.mc.lib.common.runtime;

import java.util.Objects;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;

import zone.moddev.mc.lib.common.factories.BlockEntityFactories;

/**
 * Optional base for data-configured machines. Custom blocks can delegate these same hooks
 * themselves.
 */
public class MaterialEntityBlock<E extends BlockEntity> extends Block implements EntityBlock {
  private final BlockEntityFactories.Prepared<E> factory;
  private final Supplier<BlockEntityType<E>> type;

  /**
   * A block whose block entity comes from {@code factory}.
   *
   * @param type supplies the registered type; read only when a block entity is created
   */
  public MaterialEntityBlock(
      Properties properties,
      BlockEntityFactories.Prepared<E> factory,
      Supplier<BlockEntityType<E>> type) {
    super(properties);
    this.factory = Objects.requireNonNull(factory);
    this.type = Objects.requireNonNull(type);
  }

  @Override
  public E newBlockEntity(BlockPos position, BlockState state) {
    return factory.create(type.get(), position, state);
  }

  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
      Level level, BlockState state, BlockEntityType<T> requested) {
    return factory.ticker(level, requested, type.get());
  }
}
