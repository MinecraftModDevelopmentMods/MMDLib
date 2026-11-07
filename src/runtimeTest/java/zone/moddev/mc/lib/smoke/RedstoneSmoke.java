package zone.moddev.mc.lib.smoke;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

import zone.moddev.mc.lib.api.MaterialEventContexts.RedstoneChange;
import zone.moddev.mc.lib.common.runtime.RedstoneBlock;

/**
 * A directional, delayed redstone gate built from definition data: {@code mmdlib:test_nand_gate}
 * (test resources) reads only its left and right sides and powers only its front, two ticks after
 * its inputs change.
 */
public final class RedstoneSmoke {
  private RedstoneSmoke() {}

  /** The gate's {@code redstone-changed} handler: off only while both sides are powered. */
  public static void nand(RedstoneChange change) {
    var block = (RedstoneBlock) change.state().getBlock();
    boolean left = change.signal(block.toWorld(change.state(), Direction.WEST)) > 0;
    boolean right = change.signal(block.toWorld(change.state(), Direction.EAST)) > 0;
    RedstoneBlock.setOutput(change.level(), change.position(), left && right ? 0 : 15);
  }

  /** Places the gate facing east and drives its sides through every input combination. */
  static void gate(GameTestHelper helper, BlockPos center) {
    var level = helper.getLevel();
    var pos = helper.absolutePos(center);
    var gate =
        (RedstoneBlock) BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mmdlib:test_nand_gate"));
    level.setBlock(pos, gate.defaultBlockState().setValue(RedstoneBlock.FACING, Direction.EAST), 3);
    // Facing east: the front is east, the left north, the right south and the back west.
    var left = pos.north();
    var right = pos.south();
    var back = pos.west();
    helper
        .startSequence()
        .thenExecuteAfter(3, () -> expect(helper, pos, 15, "unpowered gate is on"))
        .thenExecute(() -> level.setBlock(back, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3))
        .thenExecuteAfter(3, () -> expect(helper, pos, 15, "the back side is not read"))
        .thenExecute(() -> level.setBlock(left, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3))
        .thenExecuteAfter(3, () -> expect(helper, pos, 15, "one input keeps it on"))
        .thenExecute(() -> level.setBlock(right, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3))
        .thenExecute(() -> expect(helper, pos, 15, "the change waits for the delay"))
        .thenExecuteAfter(3, () -> expect(helper, pos, 0, "both inputs turn it off"))
        .thenExecute(() -> level.setBlock(left, Blocks.AIR.defaultBlockState(), 3))
        .thenExecuteAfter(3, () -> expect(helper, pos, 15, "dropping one input turns it on"))
        .thenSucceed();
  }

  private static void expect(GameTestHelper helper, BlockPos pos, int output, String what) {
    var level = helper.getLevel();
    var state = level.getBlockState(pos);
    int stored = state.getValue(RedstoneBlock.OUTPUT);
    // getSignal's direction points from the asking neighbor to the gate.
    int front = state.getSignal(level, pos, Direction.WEST);
    int back = state.getSignal(level, pos, Direction.EAST);
    int side = state.getSignal(level, pos, Direction.SOUTH);
    if (stored != output || front != output || back != 0 || side != 0)
      throw new AssertionError(
          what + ": expected front " + output + ", got stored " + stored + ", front " + front
              + ", back " + back + ", side " + side);
  }
}
