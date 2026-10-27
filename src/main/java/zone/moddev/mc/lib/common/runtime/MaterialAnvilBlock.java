package zone.moddev.mc.lib.common.runtime;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/** A single registry ID retaining the 1.12 anvil damage states. */
public final class MaterialAnvilBlock extends AnvilBlock {
    public static final IntegerProperty DAMAGE = IntegerProperty.create("damage", 0, 2);

    public MaterialAnvilBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(DAMAGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DAMAGE);
    }

    /** The GUI opens only through the definition's block-right-click binding (mmdlib:open_menu by default). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider((containerId, inventory, player) ->
                new DurableAnvilMenu(containerId, inventory, level, pos),
                net.minecraft.network.chat.Component.translatable(getDescriptionId()));
    }

    private static final class DurableAnvilMenu extends AnvilMenu {
        private final Level level;
        private final BlockPos pos;

        private DurableAnvilMenu(int containerId, Inventory inventory, Level level, BlockPos pos) {
            super(containerId, inventory, ContainerLevelAccess.create(level, pos));
            this.level = level;
            this.pos = pos.immutable();
        }

        @Override
        protected boolean isValidBlock(BlockState state) {
            return state.getBlock() instanceof MaterialAnvilBlock;
        }

        @Override
        protected void onTake(Player player, ItemStack output) {
            BlockState original = level.getBlockState(pos);
            if (!(original.getBlock() instanceof MaterialAnvilBlock)) {
                super.onTake(player, output);
                return;
            }

            int damage = original.getValue(DAMAGE);
            Block placeholder = switch (damage) {
                case 0 -> Blocks.ANVIL;
                case 1 -> Blocks.CHIPPED_ANVIL;
                default -> Blocks.DAMAGED_ANVIL;
            };
            level.setBlock(pos, placeholder.defaultBlockState().setValue(FACING, original.getValue(FACING)), 2);
            super.onTake(player, output);

            BlockState after = level.getBlockState(pos);
            if (after.is(placeholder)) {
                level.setBlock(pos, original, 2);
            } else if (damage < 2 && (after.is(Blocks.CHIPPED_ANVIL) || after.is(Blocks.DAMAGED_ANVIL))) {
                level.setBlock(pos, original.setValue(DAMAGE, damage + 1), 2);
            }
        }
    }
}
