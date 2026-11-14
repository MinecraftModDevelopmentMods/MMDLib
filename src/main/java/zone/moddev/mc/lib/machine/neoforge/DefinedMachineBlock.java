package zone.moddev.mc.lib.machine.neoforge;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

import zone.moddev.mc.lib.common.runtime.DropsContents;

/** Standard placement, interaction and lifecycle hooks for XML machines. */
public final class DefinedMachineBlock extends Block implements EntityBlock {
    private final Supplier<BlockEntityType<DefinedMachineEntity>> type;

    public DefinedMachineBlock(Properties properties,
        Supplier<BlockEntityType<DefinedMachineEntity>> type) {
        super(properties);
        this.type = type;
        registerDefaultState(stateDefinition.any()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(
        StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override
    protected void tick(BlockState state,
        net.minecraft.server.level.ServerLevel level, BlockPos pos,
        net.minecraft.util.RandomSource random) {
        if (level.getBlockEntity(pos) instanceof DefinedMachineEntity machine)
            machine.flushVisuals();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(
            BlockStateProperties.HORIZONTAL_FACING,
            context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(BlockStateProperties.HORIZONTAL_FACING, rotation
            .rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(
            state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return type.get().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
        BlockState state, BlockEntityType<T> requested) {
        if (requested != type.get())
            return null;
        return level.isClientSide()
            ? (world, pos, s, entity) -> ((DefinedMachineEntity) entity)
                .clientTick()
            : (world, pos, s, entity) -> ((DefinedMachineEntity) entity)
                .serverTick();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
        BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()
            && level.getBlockEntity(pos) instanceof DefinedMachineEntity host
            && host.definition.behavior().secondaryAction() != null) {
            var action = host.definition.behavior().secondaryAction();
            if (action.equals(
                zone.moddev.mc.lib.machine.feature.PortableStoredProcessor.PICKUP))
                return InteractionResult.FAIL; // Pickup requires the UI or a
                                               // tagged tool.
            var processor = host.screenFeatures()
                .findProcessor(host.definition.processor()).orElseThrow();
            if (level.isClientSide())
                return processor.actionAvailable(action, player)
                    ? InteractionResult.SUCCESS
                    : InteractionResult.FAIL;
            var result = processor.invoke(action, player);
            result.message().ifPresent(
                message -> player.displayClientMessage(message, true));
            return result.accepted()
                ? InteractionResult.CONSUME
                : InteractionResult.FAIL;
        }
        if (level.getBlockEntity(pos) instanceof DefinedMachineEntity host
            && host.portable() != null) {
            var result = host.portable().use(player, player.isShiftKeyDown());
            if (result != InteractionResult.PASS)
                return result;
        }
        return level.getBlockEntity(pos) instanceof DefinedMachineEntity machine
            && machine.openScreen(player)
                ? InteractionResult.sidedSuccess(level.isClientSide())
                : InteractionResult.PASS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state,
        Level level, BlockPos pos, Player player, InteractionHand hand,
        BlockHitResult hit) {
        if (player.isSecondaryUseActive()
            && stack.is(MachineContent.PICKUP_TOOLS)
            && level.getBlockEntity(pos) instanceof DefinedMachineEntity host
            && host.portable() != null) {
            if (!level.isClientSide()) {
                var processor = host.screenFeatures()
                    .findProcessor(host.definition.processor()).orElseThrow();
                var result = processor.invoke(
                    zone.moddev.mc.lib.machine.feature.PortableStoredProcessor.PICKUP,
                    player);
                result.message().ifPresent(
                    message -> player.displayClientMessage(message, true));
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        if (stack.is(MachineContent.recoveryItem()) && level
            .getBlockEntity(pos) instanceof DefinedMachineEntity machine) {
            if (level.isClientSide())
                return ItemInteractionResult.SUCCESS;
            return MachineRecoveryItem.restore(stack, machine)
                ? ItemInteractionResult.CONSUME
                : ItemInteractionResult.FAIL;
        }
        return MachineItemInteractions.useOnBlock(
            new net.minecraft.world.item.context.UseOnContext(player, hand,
                hit));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
        BlockState previous, boolean moved) {
        super.onPlace(state, level, pos, previous, moved);
        if (!level.isClientSide()
            && level.getBlockEntity(pos) instanceof DefinedMachineEntity host)
            host.completePlacement();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
        BlockState replacement, boolean moved) {
        if (!level.restoringBlockSnapshots)
            DropsContents.onRemove(state, level, pos, replacement);
        super.onRemove(state, level, pos, replacement, moved);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos,
        BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative()
            && level.getBlockEntity(pos) instanceof DefinedMachineEntity host
            && host.portable() != null)
            host.portable().creativeDestroy();
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(
        BlockState state, BlockGetter world, BlockPos pos,
        net.minecraft.world.phys.shapes.CollisionContext context) {
        if (!(world.getBlockEntity(pos) instanceof DefinedMachineEntity host)
            || host.definition.behavior().shapes().isEmpty())
            return super.getShape(state, world, pos, context);
        return host
            .shape(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level,
        BlockPos pos, @Nullable Direction side) {
        return level.getBlockEntity(pos) instanceof DefinedMachineEntity machine
            && machine.screenRedstone() != null
            && machine.screenRedstone().connects(side);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos,
        Direction side) {
        return level.getBlockEntity(pos) instanceof DefinedMachineEntity machine
            && machine.screenRedstone() != null
                ? machine.screenRedstone().signal(side)
                : 0;
    }
}
