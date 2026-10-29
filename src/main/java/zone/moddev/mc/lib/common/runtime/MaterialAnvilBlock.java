package zone.moddev.mc.lib.common.runtime;

import java.util.ArrayList;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * {@code mmdlib:anvil}: one registry ID per material, with the vanilla anvil's three wear stages
 * kept in a {@code damage} property (0-2) as in 1.12. Vanilla's anvil code only knows the vanilla
 * anvil, chipped anvil and damaged anvil blocks, so this class adapts the two places where it wears
 * an anvil: taking a result from the menu, and landing after a fall. A dropped or mined anvil keeps
 * its wear in the item's {@code minecraft:block_state} component.
 */
public final class MaterialAnvilBlock extends AnvilBlock {
  public static final IntegerProperty DAMAGE = IntegerProperty.create("damage", 0, 2);

  /** An anvil at damage 0. */
  public MaterialAnvilBlock(Properties properties) {
    super(properties);
    registerDefaultState(defaultBlockState().setValue(DAMAGE, 0));
  }

  /**
   * Vanilla rolls for fall damage with {@code AnvilBlock.damage}, which returns null for any anvil
   * but the vanilla ones; the falling block then destroys itself instead of landing one stage
   * worse. Both that case and the drop-as-item cases reach this hook; they are told apart by the
   * falling block's saved {@code CancelDrop} flag, which only the damage roll (or another mod's
   * {@code disableDrop}) sets.
   */
  @Override
  public void onBrokenAfterFall(Level level, BlockPos pos, FallingBlockEntity falling) {
    BlockState state = falling.getBlockState();
    if (level.isClientSide || !state.is(this)) {
      super.onBrokenAfterFall(level, pos, falling);
      return;
    }
    int damage = state.getValue(DAMAGE);
    boolean cancelled = falling.saveWithoutId(new CompoundTag()).getBoolean("CancelDrop");
    if (!cancelled) {
      // Landed where it cannot be placed: vanilla drops a plain item next. Drop one that keeps
      // the wear instead, and capture vanilla's so it never enters the world.
      if (damage > 0) {
        falling.spawnAtLocation(stack(damage));
        falling.captureDrops(new ArrayList<>());
      }
      super.onBrokenAfterFall(level, pos, falling);
      return;
    }
    if (damage >= 2) {
      super.onBrokenAfterFall(level, pos, falling); // Fully worn: it breaks, as vanilla's does.
      return;
    }
    BlockState worn = state.setValue(DAMAGE, damage + 1);
    boolean replaceable =
        level
            .getBlockState(pos)
            .canBeReplaced(
                new DirectionalPlaceContext(
                    level, pos, Direction.DOWN, ItemStack.EMPTY, Direction.UP));
    if (replaceable && worn.canSurvive(level, pos) && level.setBlock(pos, worn, 3)) {
      onLand(level, pos, worn, level.getBlockState(pos), falling);
    } else if (falling.dropItem && level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
      falling.spawnAtLocation(stack(damage + 1));
    }
  }

  /** This anvil's item at wear stage {@code damage}. */
  public ItemStack stack(int damage) {
    var stack = new ItemStack(this);
    if (damage > 0)
      stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(DAMAGE, damage));
    return stack;
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    super.createBlockStateDefinition(builder);
    builder.add(DAMAGE);
  }

  /**
   * The GUI opens only through the definition's block-right-click binding (mmdlib:open_menu by
   * default).
   */
  @Override
  protected InteractionResult useWithoutItem(
      BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    return InteractionResult.PASS;
  }

  @Nullable
  @Override
  public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
    return new SimpleMenuProvider(
        (containerId, inventory, player) ->
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
      Block placeholder =
          switch (damage) {
            case 0 -> Blocks.ANVIL;
            case 1 -> Blocks.CHIPPED_ANVIL;
            default -> Blocks.DAMAGED_ANVIL;
          };
      level.setBlock(
          pos, placeholder.defaultBlockState().setValue(FACING, original.getValue(FACING)), 2);
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
