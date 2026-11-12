package zone.moddev.mc.lib.machine.neoforge;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

import zone.moddev.mc.lib.gui.runtime.ScreenRuntime;
import zone.moddev.mc.lib.machine.multiblock.MultiblockController;
import zone.moddev.mc.lib.machine.multiblock.MultiblockInstance;

/**
 * Optional block wiring for controllers and physical parts; shapes and item interactions are
 * overridable.
 */
public class MultiblockBlock extends Block implements EntityBlock {
    private final Supplier<? extends BlockEntityType<?>> type;
    private final boolean controller;
    public MultiblockBlock(Properties properties, Supplier<? extends BlockEntityType<?>> type) {
        this(properties, type, true);
    }
    public MultiblockBlock(Properties properties, Supplier<? extends BlockEntityType<?>> type,
        boolean controller) {
        super(properties);
        this.type = type;
        this.controller = controller;
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING,
            Direction.NORTH));
    }
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return type.get().create(pos, state);
    }
    @Override
    public BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(BlockStateProperties.HORIZONTAL_FACING,
            rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }
    @Override
    public BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return rotate(state,
            mirror.getRotation(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
    }
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
        BlockEntityType<T> requested) {
        if (!controller || level.isClientSide || requested != type.get())
            return null;
        return (world, pos, block, entity) -> {
            if (entity instanceof BaseMachine machine)
                machine.serverTick();
        };
    }
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        MultiblockMembers.reconcile(level, pos);
    }
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
        Player player, BlockHitResult hit) {
        if (level.isClientSide)
            return InteractionResult.SUCCESS;
        var instance = MultiblockMembers.resolve(level, pos);
        var entity = instance == null ? level.getBlockEntity(pos) : instance.owner();
        return ScreenRuntime.open(player, entity, pos)
            ? InteractionResult.CONSUME
            : InteractionResult.PASS;
    }
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state,
        Player player) {
        if (!level.isClientSide) {
            var instance = MultiblockMembers.resolve(level, pos);
            if (instance != null)
                instance.dismantle(player.isCreative()
                    ? MultiblockInstance.Cause.CREATIVE
                    : MultiblockInstance.Cause.SURVIVAL);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement,
        boolean moved) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide
            && !MultiblockPlacement.writing(level, pos)) {
            var entity = level.getBlockEntity(pos);
            var link = MultiblockMembers.INSTANCE.link(level, pos);
            var instance = entity instanceof MultiblockController controller
                ? controller.multiblock()
                : null;
            if (instance == null && link != null && level.hasChunkAt(link.controller())
                && level
                    .getBlockEntity(link.controller()) instanceof MultiblockController controller
                && controller.multiblock() != null
                && controller.multiblock().contains(pos, link))
                instance = controller.multiblock();
            if (instance != null)
                instance.dismantle(MultiblockInstance.Cause.RECOVERY);
        }
        super.onRemove(state, level, pos, replacement, moved);
    }
}
