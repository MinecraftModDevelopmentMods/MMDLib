package zone.moddev.mc.companion.test;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RedstoneSide;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import zone.moddev.mc.companion.CompanionMod;
import zone.moddev.mc.lib.api.capabilities.RelativeFace;
import zone.moddev.mc.lib.api.redstone.RedstoneControl;
import zone.moddev.mc.lib.machine.neoforge.BaseMachine;

@GameTestHolder("mmdlib_companion")
@PrefixGameTestTemplate(false)
public final class RedstoneConnectionTests {
    @GameTest(template = "empty")
    public static void loadingConnectionsDoesNotPulseObservers(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var observerPos = pos.west();
        level.setBlock(pos, CompanionMod.CRUSHER.block.get().defaultBlockState(), 3);
        level.setBlock(observerPos, Blocks.OBSERVER.defaultBlockState()
            .setValue(BlockStateProperties.FACING, Direction.EAST), 2);
        var machine = (BaseMachine) level.getBlockEntity(pos);
        var control = machine.screenRedstone();
        int face = RelativeFace.of(Direction.NORTH, Direction.EAST).ordinal();
        control.mode(face, RedstoneControl.Mode.READ);
        control.updateOutputs();
        require(!level.getBlockTicks().hasScheduledTick(observerPos, Blocks.OBSERVER),
            "First connection tick does not pulse an observer facing the machine");
        var saved = machine.saveWithFullMetadata(level.registryAccess());
        var restored = (BaseMachine) BlockEntity.loadStatic(pos, machine.getBlockState(), saved,
            level.registryAccess());
        level.setBlockEntity(restored);
        restored.screenRedstone().updateOutputs();
        require(!level.getBlockTicks().hasScheduledTick(observerPos, Blocks.OBSERVER),
            "Restoring configured connections does not pulse an observer");
        restored.screenRedstone().mode(face, RedstoneControl.Mode.IGNORE);
        restored.screenRedstone().updateOutputs();
        require(level.getBlockTicks().hasScheduledTick(observerPos, Blocks.OBSERVER),
            "An actual configuration change still updates neighbouring shapes");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dustConnectsOnlyToConfiguredInputsAndOutputs(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var dust = pos.east();
        var line = Blocks.REDSTONE_WIRE.defaultBlockState()
            .setValue(RedStoneWireBlock.NORTH, RedstoneSide.SIDE)
            .setValue(RedStoneWireBlock.SOUTH, RedstoneSide.SIDE);
        for (var block : List.of(CompanionMod.CRUSHER.block.get(),
            BuiltInRegistries.BLOCK
                .get(ResourceLocation.parse("mmdlib_companion:simple_crusher")))) {
            for (var facing : Direction.Plane.HORIZONTAL) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                var state = block.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
                level.setBlock(pos, state, 3);
                var machine = (BaseMachine) level.getBlockEntity(pos);
                var control = machine.screenRedstone();
                int face = RelativeFace.of(facing, Direction.EAST).ordinal();
                for (var wirePos : List.of(dust.north(), dust, dust.south())) {
                    level.setBlock(wirePos.below(), Blocks.STONE.defaultBlockState(), 3);
                    level.setBlock(wirePos, line, 3);
                }
                require(!state.canRedstoneConnectTo(level, pos, null),
                    "No vertical dust connection");
                for (var query : Direction.values())
                    require(!state.canRedstoneConnectTo(level, pos, query), "Ignore refuses dust");
                level.setBlock(dust, line.setValue(RedStoneWireBlock.WEST, RedstoneSide.SIDE), 2);
                control.updateOutputs();
                require(
                    level.getBlockState(dust).getValue(RedStoneWireBlock.WEST) == RedstoneSide.NONE,
                    "First tick repairs saved dust geometry beside an Ignore face");
                control.mode(face, RedstoneControl.Mode.EMIT);
                control.updateOutputs();
                require(!state.canRedstoneConnectTo(level, pos, Direction.WEST),
                    "Emit without an output selection refuses dust");
                require(control.source(face, 0), "An output source is available");
                control.updateOutputs();
                require(control.signal(Direction.WEST) == 0, "Selected idle output is zero");
                for (var query : Direction.values())
                    require(
                        state.canRedstoneConnectTo(level, pos, query) == (query == Direction.WEST),
                        "Only the selected face connects, respecting machine orientation");
                require(
                    level.getBlockState(dust).getValue(RedStoneWireBlock.WEST) == RedstoneSide.SIDE,
                    "Selecting a zero-strength output updates existing dust geometry");
                control.inverted(face, true);
                control.updateOutputs();
                require(level.getBlockState(dust).getValue(RedStoneWireBlock.POWER) == 15,
                    "Connected dust receives power when the output rises");
                control.inverted(face, false);
                control.updateOutputs();
                require(level.getBlockState(dust).getValue(RedStoneWireBlock.POWER) == 0
                    && level.getBlockState(dust)
                        .getValue(RedStoneWireBlock.WEST) == RedstoneSide.SIDE,
                    "Falling output strength keeps the configured connection");

                var restored = (BaseMachine) BlockEntity.loadStatic(pos, state,
                    machine.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
                require(restored.screenRedstone().connects(Direction.WEST),
                    "Configured connection survives save/load");
                var client = (BaseMachine) ((EntityBlock) block)
                    .newBlockEntity(pos, state);
                client.handleUpdateTag(machine.getUpdateTag(level.registryAccess()),
                    level.registryAccess());
                require(client.screenRedstone().connects(Direction.WEST),
                    "Chunk/update tag carries connections without an open menu");

                for (var mode : List.of(RedstoneControl.Mode.READ, RedstoneControl.Mode.IGNORE)) {
                    control.mode(face, mode);
                    control.updateOutputs();
                    boolean connected = mode == RedstoneControl.Mode.READ;
                    require(state.canRedstoneConnectTo(level, pos, Direction.WEST) == connected,
                        "Read accepts dust and Ignore refuses it while retaining the selection");
                    require(
                        level.getBlockState(dust)
                            .getValue(RedStoneWireBlock.WEST).isConnected() == connected,
                        "Dust geometry follows configured input/output faces at zero strength");
                    client.handleUpdateTag(machine.getUpdateTag(level.registryAccess()),
                        level.registryAccess());
                    require(client.screenRedstone().connects(Direction.WEST) == connected,
                        "Client update preserves Read and clears Ignore connections");
                    control.mode(face, RedstoneControl.Mode.EMIT);
                    control.updateOutputs();
                }
                var missing = control.save(level.registryAccess());
                missing.putString("source_" + face, "removed/output");
                control.load(missing, level.registryAccess());
                control.updateOutputs();
                require(!state.canRedstoneConnectTo(level, pos, Direction.WEST)
                    && level.getBlockState(dust)
                        .getValue(RedStoneWireBlock.WEST) == RedstoneSide.NONE,
                    "A removed output source disconnects dust");
                control.mode(face, RedstoneControl.Mode.READ);
                control.updateOutputs();
                require(state.canRedstoneConnectTo(level, pos, Direction.WEST)
                    && level.getBlockState(dust).getValue(RedStoneWireBlock.WEST).isConnected(),
                    "Read connects without an available output source");
                level.setBlock(dust.north().north(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
                control.readInputs();
                require(control.incoming(face) > 0 && control.paused(),
                    "A connected side branch delivers dust input and gates processing");
                level.setBlock(dust.north().north(), Blocks.AIR.defaultBlockState(), 3);
                for (var wirePos : List.of(dust.north(), dust, dust.south()))
                    level.setBlock(wirePos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        helper.succeed();
    }

    private static void require(boolean value, String message) {
        if (!value)
            throw new AssertionError(message);
    }
}
