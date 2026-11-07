package zone.moddev.mc.lib.smoke;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import zone.moddev.mc.lib.common.runtime.RedstoneBlock;

/**
 * The hooks as CraftTweaker scripts see them: {@code src/runtimeTest/scripts/mmdlib_hooks.zs}
 * builds {@code mmdlib:script_hook_tool} and {@code mmdlib:script_hook_block}, whose handlers leave
 * marks (fire ticks, mined blocks, redstone output) that these checks look for.
 */
final class ScriptHookSmoke {
  private ScriptHookSmoke() {}

  private static ItemStack stack(String id) {
    return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
  }

  private static ServerPlayer player(GameTestHelper helper, ItemStack held) {
    var player = helper.makeMockServerPlayerInLevel();
    player.setGameMode(GameType.SURVIVAL);
    player.setItemInHand(InteractionHand.MAIN_HAND, held);
    return player;
  }

  /** Right-click on air, use start (duration 10 cut to 3) and finish. */
  static void item(GameTestHelper helper, BlockPos center) {
    var level = helper.getLevel();
    var player = player(helper, stack("mmdlib:script_hook_tool"));
    player.gameMode.useItem(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND);
    require(player.getRemainingFireTicks() == 33, "onRightClick on air ran");
    require(player.isUsingItem() && player.getUseItemRemainingTicks() == 3, "onUseStart set 3 ticks");
    for (int i = 0; i < 4 && player.isUsingItem(); i++) player.doTick();
    require(!player.isUsingItem() && player.getRemainingFireTicks() >= 70, "onUseFinish ran");

    var target = helper.spawnWithNoFreeWill(EntityType.PIG, center);
    player.attack(target);
    require(target.getRemainingFireTicks() == 100, "onHit ran");

    var pos = helper.absolutePos(center).north(2);
    level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
    level.setBlock(pos.above(), Blocks.DIRT.defaultBlockState(), 3);
    require(player.gameMode.destroyBlock(pos), "the block breaks");
    require(level.getBlockState(pos.above()).isAir(), "onBreakBlock mined the block above");
    helper.succeed();
  }

  /** Placed, right-clicked, stepped on, randomly ticked, and kept or broken. */
  static void block(GameTestHelper helper, BlockPos center) {
    var level = helper.getLevel();
    var floor = helper.absolutePos(center);
    level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
    var pos = floor.above();
    var player = player(helper, stack("mmdlib:script_hook_block"));
    var top = new BlockHitResult(Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false);
    player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, top));
    require(output(helper, pos) == 4, "onPlaced set the output to 4");

    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    var side = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);
    player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, side);
    require(output(helper, pos) == 5, "onRightClick raised the output");

    var state = level.getBlockState(pos);
    state.getBlock().stepOn(level, pos, state, player);
    require(player.getRemainingFireTicks() == 60, "onSteppedOn ran");
    state.randomTick(level, pos, level.getRandom());
    require(output(helper, pos) == 9, "onRandomTick set the output to 9");

    player.setShiftKeyDown(true);
    player.gameMode.destroyBlock(pos);
    require(!level.getBlockState(pos).isAir(), "onBroken cancelled a sneaking break");
    player.setShiftKeyDown(false);
    require(player.gameMode.destroyBlock(pos) && level.getBlockState(pos).isAir(), "it breaks");
    helper.succeed();
  }

  /**
   * Mining a wall of dirt face-on with the hammer breaks the 3x3 square around the block, each
   * block once: nine dirt items, and a block behind the wall is untouched.
   */
  static void hammer(GameTestHelper helper, BlockPos center) {
    var level = helper.getLevel();
    var middle = helper.absolutePos(center).above();
    for (int dx = -1; dx <= 1; dx++)
      for (int dy = -1; dy <= 1; dy++)
        level.setBlock(middle.offset(dx, dy, 0), Blocks.DIRT.defaultBlockState(), 3);
    level.setBlock(middle.north(), Blocks.DIRT.defaultBlockState(), 3);
    var player = player(helper, stack("mmdlib:script_hammer"));
    // Stand south of the wall, looking north at its middle block, so the mined face is south.
    player.moveTo(middle.getX() + .5, middle.getY() - 1.0, middle.getZ() + 2.5, 180, 0);
    // A living entity looks along its head rotation, which moveTo leaves alone.
    player.setYHeadRot(180);
    player.yHeadRotO = 180;
    require(player.gameMode.destroyBlock(middle), "the middle block breaks");
    var left = new java.util.ArrayList<String>();
    for (int dx = -1; dx <= 1; dx++)
      for (int dy = -1; dy <= 1; dy++)
        if (!level.getBlockState(middle.offset(dx, dy, 0)).isAir()) left.add(dx + "," + dy);
    require(left.isEmpty(), "the 3x3 square is mined; left " + left);
    require(!level.getBlockState(middle.north()).isAir(), "the block behind is kept");
    var drops =
        level.getEntitiesOfClass(
            net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(middle).inflate(3));
    int dirt = drops.stream().mapToInt(item -> item.getItem().getCount()).sum();
    require(dirt == 9, "each block dropped once, got " + dirt);
    helper.succeed();
  }

  private static int output(GameTestHelper helper, BlockPos pos) {
    return helper.getLevel().getBlockState(pos).getValue(RedstoneBlock.OUTPUT);
  }

  private static void require(boolean condition, String what) {
    if (!condition) throw new AssertionError(what);
  }
}
