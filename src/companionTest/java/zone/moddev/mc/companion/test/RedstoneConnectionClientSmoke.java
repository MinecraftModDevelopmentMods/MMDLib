package zone.moddev.mc.companion.test;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.properties.RedstoneSide;

import zone.moddev.mc.companion.ProcessorEntity;
import zone.moddev.mc.lib.api.capabilities.RelativeFace;
import zone.moddev.mc.lib.api.redstone.RedstoneControl;
import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.runtime.RedstoneMenu;
import zone.moddev.mc.lib.gui.runtime.component.BuiltinMenuComponents;

/**
 * Exercises real server requests and client world packets, without directly loading update tags.
 */
final class RedstoneConnectionClientSmoke {
    private static int step, waiting;
    private static volatile boolean ready;

    static boolean run(MachineScreen screen) {
        var mc = Minecraft.getInstance();
        var menu = screen.getMenu();
        var model = menu.components().get(BuiltinMenuComponents.REDSTONE);
        var pos = menu.blockEntity.getBlockPos();
        var dust = pos.east();
        int face = RelativeFace
            .of(RelativeFace.facing(menu.blockEntity.getBlockState()), Direction.EAST).ordinal();
        if (model.pending())
            return false;
        switch (step) {
            case 0 -> {
                mc.getSingleplayerServer().execute(() -> {
                    try {
                        var level = mc.getSingleplayerServer().overworld();
                        var machine = (ProcessorEntity) level.getBlockEntity(pos);
                        machine.energy.setStored(0);
                        for (int i = 0; i < 6; i++)
                            machine.redstone.mode(i, RedstoneControl.Mode.IGNORE);
                        var line = Blocks.REDSTONE_WIRE.defaultBlockState()
                            .setValue(RedStoneWireBlock.NORTH, RedstoneSide.SIDE)
                            .setValue(RedStoneWireBlock.SOUTH, RedstoneSide.SIDE);
                        for (var wirePos : List.of(dust.north(), dust, dust.south())) {
                            level.setBlock(wirePos.below(), Blocks.STONE.defaultBlockState(), 3);
                            level.setBlock(wirePos, line, 3);
                        }
                        ready = true;
                    } catch (Throwable error) {
                        CompanionClientSmoke.fail(error);
                    }
                });
            }
            case 1 -> {
                if (!ready || !mc.level.getBlockState(dust).is(Blocks.REDSTONE_WIRE))
                    return false;
                if (!model.open())
                    model.send(RedstoneMenu.TOGGLE, 0, 0, 0);
            }
            case 2 -> model.send(RedstoneMenu.SELECT, face, 0, 0);
            case 3 -> model.send(RedstoneMenu.MODE, face, 0, RedstoneControl.Mode.EMIT.ordinal());
            case 4 -> {
                int source = -1;
                for (int i = 0; i < model.control.outputs().size(); i++)
                    if (model.control.outputs().get(i).id().equals("energy"))
                        source = i;
                if (source < 0)
                    throw new AssertionError("Missing energy output");
                model.send(RedstoneMenu.SOURCE, face, 0, source);
            }
            case 5, 7, 9, 11 -> {
                boolean expected = step == 5 || step == 9;
                var block = mc.level.getBlockState(pos);
                var wire = mc.level.getBlockState(dust);
                boolean connects = block.canRedstoneConnectTo(mc.level, pos, Direction.WEST);
                boolean drawn = wire.getValue(RedStoneWireBlock.WEST).isConnected();
                if (connects != expected || drawn != expected) {
                    if (++waiting < 20)
                        return false;
                    throw new AssertionError(
                        "Client connection mismatch after drawer edit: expected=" + expected
                            + ", hook=" + connects + ", dust=" + wire + ", mode=" + model.mode(face)
                            + ", source=" + model.source(face));
                }
                waiting = 0;
            }
            case 6 -> model.send(RedstoneMenu.MODE, face, 0, RedstoneControl.Mode.IGNORE.ordinal());
            case 8 -> model.send(RedstoneMenu.MODE, face, 0, RedstoneControl.Mode.READ.ordinal());
            case 10 ->
                model.send(RedstoneMenu.MODE, face, 0, RedstoneControl.Mode.IGNORE.ordinal());
            default -> {
                return true;
            }
        }
        step++;
        return false;
    }
}
