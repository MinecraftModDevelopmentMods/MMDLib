package zone.moddev.mc.lib.machine.neoforge;

import java.util.function.Supplier;

import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;

import zone.moddev.mc.lib.common.runtime.DropsContents;

/** Standard placement, interaction and lifecycle hooks for XML machines. */
public final class DefinedMachineBlock extends Block implements EntityBlock {
  private final Supplier<BlockEntityType<DefinedMachineEntity>> type;

  public DefinedMachineBlock(
      Properties properties, Supplier<BlockEntityType<DefinedMachineEntity>> type) {
    super(properties);
    this.type = type;
    registerDefaultState(
        stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(BlockStateProperties.HORIZONTAL_FACING);
  }

  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    return defaultBlockState()
        .setValue(
            BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
  }

  @Override
  public BlockState rotate(BlockState state, Rotation rotation) {
    return state.setValue(
        BlockStateProperties.HORIZONTAL_FACING,
        rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
  }

  @Override
  public BlockState mirror(BlockState state, Mirror mirror) {
    return rotate(
        state, mirror.getRotation(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return type.get().create(pos, state);
  }

  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
      Level level, BlockState state, BlockEntityType<T> requested) {
    return level.isClientSide() || requested != type.get()
        ? null
        : (world, pos, s, entity) -> ((DefinedMachineEntity) entity).serverTick();
  }

  @Override
  protected InteractionResult useWithoutItem(
      BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    return level.getBlockEntity(pos) instanceof DefinedMachineEntity machine
            && machine.openScreen(player)
        ? InteractionResult.sidedSuccess(level.isClientSide())
        : InteractionResult.PASS;
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
    if (stack.is(MachineContent.recoveryItem())
        && level.getBlockEntity(pos) instanceof DefinedMachineEntity machine) {
      if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
      return MachineRecoveryItem.restore(stack, machine)
          ? ItemInteractionResult.CONSUME
          : ItemInteractionResult.FAIL;
    }
    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
  }

  @Override
  protected void onRemove(
      BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
    DropsContents.onRemove(state, level, pos, replacement);
    super.onRemove(state, level, pos, replacement, moved);
  }

  @Override
  protected boolean isSignalSource(BlockState state) {
    return true;
  }

  @Override
  protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
    return level.getBlockEntity(pos) instanceof DefinedMachineEntity machine
            && machine.screenRedstone() != null
        ? machine.screenRedstone().signal(side)
        : 0;
  }
}
