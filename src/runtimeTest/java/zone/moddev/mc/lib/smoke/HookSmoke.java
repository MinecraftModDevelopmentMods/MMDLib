package zone.moddev.mc.lib.smoke;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import zone.moddev.mc.lib.api.MaterialEventContexts.BlockAction;
import zone.moddev.mc.lib.api.MaterialEventContexts.BlockBreak;
import zone.moddev.mc.lib.api.MaterialEventContexts.ItemHit;
import zone.moddev.mc.lib.api.MaterialEventContexts.ItemUse;

/**
 * The item and block hooks, bound by test resources: {@code mmdlib:test_hook_tool} (used over 20
 * ticks) binds every item hook and {@code mmdlib:test_hook_block} every block hook to the handlers
 * here, through {@code mmdlib/extension/test_hooks.json}. Handlers count their calls and act.
 */
public final class HookSmoke {
  private static final Map<String, Integer> calls = new ConcurrentHashMap<>();

  private HookSmoke() {}

  private static void count(String hook) {
    calls.merge(hook, 1, Integer::sum);
  }

  private static int calls(String hook) {
    return calls.getOrDefault(hook, 0);
  }

  /** Shortens the use to 5 ticks. */
  public static void useStart(ItemUse use) {
    count("use-start");
    use.setDuration(5);
  }

  public static void useStop(ItemUse use) {
    count("use-stop");
  }

  /** Leaves a diamond in hand. */
  public static void useFinish(ItemUse use) {
    count("use-finish");
    use.setResult(new ItemStack(Items.DIAMOND));
  }

  /** Sets the target on fire. */
  public static void hit(ItemHit hit) {
    count("hit");
    if (hit.damage() > 0) hit.target().igniteForSeconds(5);
  }

  /** Also mines the block above, as a two-high mining tool would. */
  public static void breakBlock(BlockBreak broken) {
    count("break-block");
    broken.mine(broken.position().above());
  }

  public static void placed(BlockAction action) {
    count("placed");
  }

  public static void broken(BlockBreak broken) {
    count("broken");
  }

  public static void steppedOn(BlockAction action) {
    count("stepped-on");
  }

  public static void randomTick(BlockAction action) {
    count("random-tick");
  }

  private static ItemStack tool() {
    return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("mmdlib:test_hook_tool")));
  }

  /** Start, shortened duration, finish with a replaced stack; then a use released early. */
  static void use(GameTestHelper helper) {
    var player = helper.makeMockServerPlayerInLevel();
    player.setGameMode(GameType.SURVIVAL);
    player.setItemInHand(InteractionHand.MAIN_HAND, tool());
    int starts = calls("use-start"), finishes = calls("use-finish"), stops = calls("use-stop");
    player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
    require(player.isUsingItem() && player.getUseItemRemainingTicks() == 5, "use starts, 5 ticks");
    // A server player's living update runs in doTick, which its connection normally drives.
    for (int i = 0; i < 6 && player.isUsingItem(); i++) player.doTick();
    require(calls("use-start") == starts + 1 && calls("use-finish") == finishes + 1, "use finishes");
    require(player.getMainHandItem().is(Items.DIAMOND), "the finish handler replaced the stack");
    player.setItemInHand(InteractionHand.MAIN_HAND, tool());
    player.getMainHandItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
    player.releaseUsingItem();
    require(calls("use-stop") == stops + 1, "releasing early stops the use");
    helper.succeed();
  }

  /** A melee hit with the tool sets the target on fire. */
  static void hit(GameTestHelper helper, BlockPos center) {
    var player = helper.makeMockServerPlayerInLevel();
    player.setGameMode(GameType.SURVIVAL);
    player.setItemInHand(InteractionHand.MAIN_HAND, tool());
    var target = helper.spawnWithNoFreeWill(EntityType.PIG, center);
    int hits = calls("hit");
    player.attack(target);
    require(calls("hit") == hits + 1, "the hit was reported");
    require(target.getRemainingFireTicks() > 0, "the hit handler set the target on fire");
    helper.succeed();
  }

  /** Breaking one block with the tool also mines the block above, reported once. */
  static void mining(GameTestHelper helper, BlockPos center) {
    var level = helper.getLevel();
    var pos = helper.absolutePos(center);
    level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
    level.setBlock(pos.above(), Blocks.DIRT.defaultBlockState(), 3);
    var player = helper.makeMockServerPlayerInLevel();
    player.setGameMode(GameType.SURVIVAL);
    player.setItemInHand(InteractionHand.MAIN_HAND, tool());
    int breaks = calls("break-block");
    require(player.gameMode.destroyBlock(pos), "the block breaks");
    require(level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir(), "both mined");
    require(calls("break-block") == breaks + 1, "mine() does not report its own breaks again");
    helper.succeed();
  }

  /** Placing, stepping on, random ticks and breaking the hook block are each reported. */
  static void block(GameTestHelper helper, BlockPos center) {
    var level = helper.getLevel();
    var floor = helper.absolutePos(center);
    level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
    var pos = floor.above();
    var player = helper.makeMockServerPlayerInLevel();
    player.setGameMode(GameType.SURVIVAL);
    var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse("mmdlib:test_hook_block"));
    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
    int placed = calls("placed"), stepped = calls("stepped-on"), ticks = calls("random-tick"), broken = calls("broken");
    var hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, .5, 0), Direction.UP, floor, false);
    player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    var state = level.getBlockState(pos);
    require(state.getBlock() != Blocks.AIR && calls("placed") == placed + 1, "placing is reported");
    state.getBlock().stepOn(level, pos, state, player);
    require(calls("stepped-on") == stepped + 1, "stepping on is reported");
    require(state.isRandomlyTicking(), "the block takes random ticks");
    state.randomTick(level, pos, level.getRandom());
    require(calls("random-tick") == ticks + 1, "random ticks are reported");
    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    require(player.gameMode.destroyBlock(pos), "the block breaks");
    require(calls("broken") == broken + 1, "breaking is reported");
    helper.succeed();
  }

  private static void require(boolean condition, String what) {
    if (!condition) throw new AssertionError(what);
  }
}
