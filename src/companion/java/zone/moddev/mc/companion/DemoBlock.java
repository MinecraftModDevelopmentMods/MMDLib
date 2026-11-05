package zone.moddev.mc.companion;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import net.neoforged.neoforge.fluids.FluidUtil;

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
  protected ItemInteractionResult useItemOn(
      ItemStack stack,
      BlockState state,
      Level level,
      BlockPos pos,
      Player player,
      InteractionHand hand,
      BlockHitResult hit) {
    if (stack.is(CompanionAddons.RECOVERY_CAPSULE.get())
        && level.getBlockEntity(pos) instanceof ProcessorEntity machine) {
      if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
      return RecoveryCapsuleItem.restore(stack, machine)
          ? ItemInteractionResult.CONSUME
          : ItemInteractionResult.FAIL;
    }
    if (level.getBlockEntity(pos) instanceof ProcessorEntity machine
        && machine.hasTank()
        && FluidUtil.getFluidHandler(stack).isPresent()) {
      if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
      if (FluidUtil.interactWithFluidHandler(player, hand, machine.fluids))
        return ItemInteractionResult.CONSUME;
      player.displayClientMessage(
          Component.translatable("screen.mmdlib_companion.tank.refused"), true);
      // Do not let a rejected bucket spill its contents beside the machine.
      return ItemInteractionResult.FAIL;
    }
    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
  }

  @Override
  protected InteractionResult useWithoutItem(
      BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    return ScreenRuntime.open(player, level.getBlockEntity(pos))
        ? InteractionResult.sidedSuccess(level.isClientSide())
        : InteractionResult.PASS;
  }

  @Override
  protected void onRemove(
      BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
    DropsContents.onRemove(state, level, pos, replacement);
    super.onRemove(state, level, pos, replacement, moved);
  }
}
