package zone.moddev.mc.companion.test;

import java.util.List;
import java.util.Set;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import zone.moddev.mc.companion.CompanionMod;
import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.api.capabilities.RelativeFace;
import zone.moddev.mc.lib.api.capabilities.SideConfig;
import zone.moddev.mc.lib.api.redstone.RedstoneControl;
import zone.moddev.mc.lib.gui.runtime.AutomationNetwork;
import zone.moddev.mc.lib.gui.runtime.RedstoneNetwork;
import zone.moddev.mc.lib.machine.neoforge.BaseMachine;

@GameTestHolder("mmdlib_companion")
@PrefixGameTestTemplate(false)
public final class DisabledFaceTests {
    private static void require(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }

    @GameTest(template = "empty")
    public static void policySurvivesModesLoadingRotationAndCachedViews(
        GameTestHelper h) {
        var sides = new SideConfig(IoMode.BOTH);
        var items = new ItemStorage(2);
        var energy = new EnergyBuffer(1000, 100);
        energy.setStored(500);
        for (var facing : Direction.Plane.HORIZONTAL) {
            sides.disabledFaces(Set.of());
            sides.set(RelativeFace.RIGHT, IoMode.BOTH);
            var side = RelativeFace.RIGHT.toWorld(facing);
            var itemView = items.view(slot -> sides.mode(facing, side));
            var energyView = energy.view(() -> sides.mode(facing, side));
            require(itemView.insertItem(0, new ItemStack(Items.WHEAT), false)
                .isEmpty(), "Available item input");
            require(energyView.extractEnergy(10, true) == 10,
                "Available energy output");
            sides.disabledFaces(Set.of(RelativeFace.RIGHT));
            sides.set(RelativeFace.RIGHT, IoMode.BOTH);
            require(sides.cycle(RelativeFace.RIGHT) == IoMode.DISABLED,
                "Blocked cycling");
            sides.copyFrom(new SideConfig(IoMode.BOTH));
            require(sides.get(RelativeFace.RIGHT) == IoMode.DISABLED,
                "Loading modes preserves policy");
            require(
                itemView.getSlots() == 2
                    && !itemView.getStackInSlot(0).isEmpty(),
                "Stable storage indexes and readable contents");
            for (boolean simulate : List.of(true, false)) {
                require(
                    itemView.insertItem(1, new ItemStack(Items.WHEAT), simulate)
                        .getCount() == 1,
                    "Cached item input refuses");
                require(itemView.extractItem(0, 1, simulate).isEmpty(),
                    "Cached item output refuses");
                require(
                    energyView.receiveEnergy(10, simulate) == 0
                        && energyView.extractEnergy(10, simulate) == 0,
                    "Cached energy refuses");
            }
            sides.disabledFaces(Set.of());
            require(sides.get(RelativeFace.RIGHT) == IoMode.DISABLED,
                "Re-enable never resurrects a mode");
        }
        sides.disabledFaces(Set.of(RelativeFace.values()));
        for (var side : Direction.values())
            require(sides.mode(Direction.NORTH, side) == IoMode.DISABLED,
                "All physical faces unavailable");
        require(sides.mode(Direction.NORTH, null) == IoMode.BOTH,
            "Unsided internal policy remains host-owned");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void redstonePolicyStopsOutputsInputsAndLegacyOutside(
        GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        int right = RelativeFace.RIGHT.ordinal();
        for (var facing : Direction.Plane.HORIZONTAL) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(
                pos, CompanionMod.CRUSHER.block.get().defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, facing),
                3);
            var machine = (BaseMachine) level.getBlockEntity(pos);
            var control = machine.screenRedstone();
            var direction = RelativeFace.RIGHT.toWorld(facing);
            control.mode(right, RedstoneControl.Mode.EMIT);
            control.source(right, 0);
            control.inverted(right, true);
            control.updateOutputs();
            require(
                control.signal(direction.getOpposite()) == 15
                    && control.connects(direction.getOpposite()),
                "Emitting before restriction");
            var saved = control.save(level.registryAccess());
            control.disabledFaces(Set.of(RelativeFace.RIGHT));
            require(
                control.signal(direction.getOpposite()) == 0
                    && !control.connects(direction.getOpposite()),
                "Restriction immediately disconnects output");
            control.load(saved, level.registryAccess());
            require(control.mode(right) == RedstoneControl.Mode.IGNORE,
                "Saved modes cannot restore blocked emit");
            require(!control.range(right, 0, 15) && !control.source(right, 0),
                "Blocked edits rejected");
            control.mode(right, RedstoneControl.Mode.READ);
            control.outside(right, true);
            level.setBlock(pos.relative(direction),
                Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            control.readInputs();
            require(!control.paused() && control.incoming(right) == 0,
                "Unavailable input ignored");
            control.migrate(1);
            control.readInputs();
            require(control.paused(),
                "Legacy requires power on an available face");
            control.disabledFaces(Set.of(RelativeFace.values()));
            require(!control.paused(),
                "All blocked does not pause via Outside or legacy-any");
            var client = new RedstoneControl(machine, List.of());
            client.loadClient(control.saveClient(level.registryAccess()),
                level.registryAccess());
            require(client.disabledFaceMask() == 63,
                "World updates carry policy");
            require(
                !control.save(level.registryAccess())
                    .contains("disabled_faces"),
                "Disk saves exclude machine policy");
            control.disabledFaces(Set.of());
            require(control.mode(right) == RedstoneControl.Mode.IGNORE,
                "Re-enabled redstone starts Ignore");
            level.setBlock(pos.relative(direction),
                Blocks.AIR.defaultBlockState(), 3);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void boundedPolicySnapshotsRoundTrip(GameTestHelper h) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
            h.getLevel().registryAccess());
        try {
            int mask = 1 << RelativeFace.RIGHT.ordinal();
            var automation = new AutomationNetwork.State(1, 2, 3, true, 0, true,
                -1, new String[]{"food"}, new int[6], new long[4], new long[4],
                new int[]{mask});
            AutomationNetwork.State.CODEC.encode(buffer, automation);
            require(
                AutomationNetwork.State.CODEC.decode(buffer)
                    .disabledFaces()[0] == mask,
                "Automation policy round trip");
            var redstone = new RedstoneNetwork.State(1, 2, 3, 4, true, 0, false,
                new int[48], 63);
            RedstoneNetwork.State.CODEC.encode(buffer, redstone);
            require(RedstoneNetwork.State.CODEC.decode(buffer)
                .disabledFaces() == 63, "All-blocked redstone round trip");
            try {
                new RedstoneNetwork.State(1, 2, 3, 4, true, 0, false,
                    new int[48], 64);
                throw new AssertionError("Unbounded redstone mask");
            } catch (IllegalArgumentException expected) {
            }
            try {
                new AutomationNetwork.State(1, 2, 3, true, 0, true, -1,
                    new String[]{"food"}, new int[6], new long[4], new long[4],
                    new int[]{-1});
                throw new AssertionError("Unbounded automation mask");
            } catch (IllegalArgumentException expected) {
            }
        } finally {
            buffer.release();
        }
        h.succeed();
    }
}
