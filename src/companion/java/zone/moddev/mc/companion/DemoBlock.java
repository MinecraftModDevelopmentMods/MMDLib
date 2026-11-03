package zone.moddev.mc.companion;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import zone.moddev.mc.lib.common.runtime.DropsContents;
import zone.moddev.mc.lib.gui.runtime.ScreenRuntime;

/** Explicit block hooks for a code-registered machine. */
public final class DemoBlock extends Block implements EntityBlock {
  private final Supplier<BlockEntityType<DemoEntity>> type;

  public DemoBlock(Properties properties, Supplier<BlockEntityType<DemoEntity>> type) {
    super(properties);
    this.type = type;
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return type.get().create(pos, state);
  }

  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
      Level level, BlockState state, BlockEntityType<T> requested) {
    if (level.isClientSide() || requested != type.get()) return null;
    return (world, pos, blockState, entity) -> ((DemoEntity) entity).serverTick();
  }

  @Override
  protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
      Player player, BlockHitResult hit) {
    return ScreenRuntime.open(player, level.getBlockEntity(pos))
        ? InteractionResult.sidedSuccess(level.isClientSide()) : InteractionResult.PASS;
  }

  @Override
  protected void onRemove(
      BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
    if (!state.is(replacement.getBlock()) && !level.isClientSide()
        && level.getBlockEntity(pos) instanceof DropsContents contents)
      contents.dropContents(level, pos);
    super.onRemove(state, level, pos, replacement, moved);
  }
}
