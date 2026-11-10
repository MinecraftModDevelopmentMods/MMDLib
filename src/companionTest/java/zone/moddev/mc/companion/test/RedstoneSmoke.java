package zone.moddev.mc.companion.test;

import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

import zone.moddev.mc.companion.CompanionMod;
import zone.moddev.mc.companion.ProcessorEntity;
import zone.moddev.mc.lib.api.capabilities.RelativeFace;
import zone.moddev.mc.lib.api.redstone.RedstoneControl;
import zone.moddev.mc.lib.api.redstone.RedstoneOutput;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.gui.runtime.RedstoneMenu;
import zone.moddev.mc.lib.gui.runtime.RedstoneNetwork;
import zone.moddev.mc.lib.gui.runtime.component.BuiltinMenuComponents;

final class RedstoneSmoke {
    static void check(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
        int east = RelativeFace.of(Direction.NORTH, Direction.EAST).ordinal();
        for (var type : List.of(CompanionMod.CRUSHER, CompanionMod.TRANSMUTER,
            CompanionMod.DUAL_CRUSHER)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(pos, type.block.get().defaultBlockState(), 3);
            var machine = (ProcessorEntity) level.getBlockEntity(pos);
            var control = machine.redstone;
            machine.energy.setStored(100000);
            machine.items.insertItem(
                0,
                new ItemStack(
                    type == CompanionMod.TRANSMUTER ? Items.IRON_INGOT : Items.COPPER_ORE,
                    type == CompanionMod.TRANSMUTER ? 1 : 16),
                false);
            tick(machine, 6);
            require(machine.getProgress() > 0, "Ignore runs normally");
            control.mode(east, RedstoneControl.Mode.READ);
            control.outside(east, true);
            int progress = machine.getProgress();
            long energy = machine.energy.stored();
            tick(machine, 12);
            require(
                machine.getProgress() == progress
                    && machine.energy.stored() == energy
                    && machine.items.hasPlan(0),
                "Read gate preserves progress, energy and reservations");
            level.setBlock(pos.east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            tick(machine, 2);
            require(
                machine.getProgress() > progress && control.incoming(east) == 15,
                "Read observes only the configured input face");
            machine.toggle(0);
            progress = machine.getProgress();
            tick(machine, 3);
            require(
                machine.getPaused() && machine.getProgress() == progress,
                "Signal never overrides manual pause");
            machine.toggle(0);
            control.outside(east, false);
            tick(machine, 2);
            require(control.paused(), "Pause on signal");
            level.setBlock(pos.east(), Blocks.AIR.defaultBlockState(), 3);
            control.tick();
            require(!control.paused(), "No signal releases pause-on mode");
            require(control.range(east, 4, 10), "Built-in interval accepts ordered endpoints");
            require(
                !control.range(east, 12, 3) && !control.range(east, -1, 3)
                    && !control.range(east, 2, 16),
                "Invalid intervals rejected");
            for (int signal = 0; signal < 16; signal++) {
                require(
                    RedstoneControl.matches(signal, 4, 10, false) == (signal >= 4 && signal <= 10),
                    "Between includes both endpoints");
                require(
                    RedstoneControl.matches(signal, 4, 10, true) == (signal < 4 || signal > 10),
                    "Outside is the exact complement");
                require(
                    RedstoneControl.matches(signal, 7, 7, false) == (signal == 7),
                    "Equal handles select one level");
                require(
                    !RedstoneControl.matches(signal, 0, 15, true),
                    "Outside full range selects nothing");
            }
            control.outside(east, true);
            control.tick();
            require(control.paused(), "Signal zero pauses outside 4-10");
            control.range(east, 0, 0);
            control.tick();
            require(!control.paused(), "Signal zero runs outside the single zero interval");
            control.mode(east, RedstoneControl.Mode.IGNORE);
            int west = RelativeFace.of(Direction.NORTH, Direction.WEST).ordinal();
            control.mode(west, RedstoneControl.Mode.EMIT);
            control.tick();
            require(control.outgoing(west) == 0, "No selected inventory emits zero");
            control.inverted(west, true);
            control.tick();
            require(control.outgoing(west) == 0,
                "Revert without a selected inventory still emits zero");
            control.inverted(west, false);
            int group = -1;
            for (int i = 0; i < control.outputs().size(); i++)
                if (control.outputs().get(i).id().equals("energy"))
                    group = i;
            require(
                group >= 0 && control.source(west, group),
                "Output selects resource by stable group ID");
            require(!control.source(west, 9999), "Invalid group refused");
            machine.energy.setStored(machine.energy.capacity() / 2);
            control.tick();
            require(control.outgoing(west) == 7, "Half full exports seven");
            control.inverted(west, true);
            control.tick();
            require(control.outgoing(west) == 8, "Reverted half full exports eight");
            machine.energy.setStored(0);
            control.tick();
            require(control.outgoing(west) == 15, "Reverted empty exports fifteen");
            machine.energy.setStored(machine.energy.capacity());
            control.tick();
            require(control.outgoing(west) == 0, "Reverted full exports zero");
            control.inverted(west, false);
            machine.energy.setStored(machine.energy.capacity());
            control.tick();
            require(
                level.getSignal(pos, Direction.EAST) == 15
                    && level.getSignal(pos, Direction.WEST) == 0,
                "Full output travels only out of the configured face");
            control.inverted(west, true);
            control.mode(west, RedstoneControl.Mode.READ);
            control.tick();
            require(
                control.outgoing(west) == 0 && control.source(west) == group,
                "Mode change retains selection but stops emission");
            control.mode(west, RedstoneControl.Mode.EMIT);
            control.range(east, 4, 10);
            var saved = machine.saveWithFullMetadata(level.registryAccess());
            var restored = (ProcessorEntity) BlockEntity.loadStatic(pos, machine.getBlockState(),
                saved, level.registryAccess());
            require(
                restored.redstone.mode(west) == RedstoneControl.Mode.EMIT
                    && restored.redstone.source(west) == group
                    && restored.redstone.inverted(west)
                    && restored.redstone.lower(east) == 4
                    && restored.redstone.upper(east) == 10
                    && restored.redstone.outside(east),
                "Per-face range, outside and inventory survive save/load");
            var legacy = saved.copy();
            legacy.remove("redstone_control");
            legacy.putInt("redstone_mode", 1);
            var migrated = (ProcessorEntity) BlockEntity.loadStatic(pos, machine.getBlockState(),
                legacy, level.registryAccess());
            migrated.setLevel(level);
            migrated.redstone.tick();
            require(migrated.redstone.paused(), "Legacy powered mode waits without input");
            level.setBlock(pos.east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            migrated.redstone.tick();
            require(!migrated.redstone.paused(), "Legacy powered mode retains any-face behavior");
            migrated.redstone.range(0, 2, 8);
            require(
                !migrated.redstone.save(level.registryAccess()).getBoolean("legacy_any"),
                "Explicit interval edit adopts per-face rules");
            level.setBlock(pos.east(), Blocks.AIR.defaultBlockState(), 3);
            var invalid = control.save(level.registryAccess());
            invalid.putIntArray("modes", new int[]{99, -1, 0, 0, 0, 0});
            invalid.putIntArray("lower", new int[]{-1, 99, 0, 0, 0, 0});
            invalid.putIntArray("upper", new int[]{99, -1, 0, 0, 0, 0});
            restored.redstone.load(invalid, level.registryAccess());
            require(
                restored.redstone.mode(0) == RedstoneControl.Mode.IGNORE
                    && restored.redstone.mode(1) == RedstoneControl.Mode.IGNORE
                    && restored.redstone.lower(0) == 0
                    && restored.redstone.upper(0) == 15
                    && restored.redstone.lower(1) == 15
                    && restored.redstone.upper(1) == 15,
                "Malformed saved modes and ranges normalized");
            var player = helper.makeMockServerPlayerInLevel();
            player.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
            var menu = new MachineMenu(87, player.getInventory(), machine);
            player.containerMenu = menu;
            require(menu.bound.screen().find("redstone").isEmpty(),
                "Old main-panel control is absent");
            var drawer = menu.components().get(BuiltinMenuComponents.REDSTONE);
            drawer.action(player, request(menu, 1, RedstoneMenu.TOGGLE, 0, 0));
            require(drawer.open(), "Drawer opens through session-bound action");
            drawer.action(player, request(menu, 2, RedstoneMenu.MODE, 0, 1));
            require(control.mode(0) == RedstoneControl.Mode.READ,
                "Selected face changes through menu");
            var stale = request(menu, 3, RedstoneMenu.MODE, 0, 2);
            control.outside(0, true);
            drawer.action(player, stale);
            require(control.mode(0) == RedstoneControl.Mode.READ, "Stale face edit rejected");
            drawer.action(
                player,
                new RedstoneNetwork.Action(
                    menu.containerId,
                    menu.widgetSession() + 1,
                    4,
                    control.revision(),
                    RedstoneMenu.MODE,
                    0,
                    0,
                    2));
            require(control.mode(0) == RedstoneControl.Mode.READ, "Foreign session edit rejected");
            drawer.action(player, request(menu, 5, RedstoneMenu.MODE, 1, 2));
            require(control.mode(1) == RedstoneControl.Mode.IGNORE,
                "Cannot edit a nonselected face");
            drawer.action(player, request(menu, 6, RedstoneMenu.RANGE, 0, 4 * 16 + 10));
            require(control.lower(0) == 4 && control.upper(0) == 10,
                "Range command applies atomically");
            drawer.action(player, request(menu, 7, RedstoneMenu.RANGE, 0, 12 * 16 + 3));
            require(control.lower(0) == 4 && control.upper(0) == 10,
                "Unordered range command refused");
            int beforeGroup = control.source(0);
            drawer.action(player, request(menu, 8, RedstoneMenu.SOURCE, 0, group));
            require(control.source(0) == beforeGroup,
                "Read face refuses output-only group command");
            drawer.action(player, request(menu, 9, RedstoneMenu.INVERT, 0, 1));
            require(!control.inverted(0), "Read face refuses output inversion");
            drawer.action(player, request(menu, 10, RedstoneMenu.MODE, 0, 2));
            drawer.action(player, request(menu, 11, RedstoneMenu.INVERT, 0, 1));
            require(control.inverted(0), "Output inversion applied through validated menu action");
            drawer.action(player, request(menu, 12, RedstoneMenu.INVERT, 0, 2));
            require(control.inverted(0), "Invalid inversion value refused");
            var staleClose = request(menu, 13, RedstoneMenu.TOGGLE, 0, 0);
            control.range(0, 3, 11); // Another viewer changes the shared configuration.
            drawer.action(player, staleClose);
            require(!drawer.open(), "Shared edits cannot block local drawer close");
            var staleOpen = request(menu, 14, RedstoneMenu.TOGGLE, 0, 0);
            control.range(0, 4, 11);
            drawer.action(player, staleOpen);
            require(drawer.open(), "Shared edits cannot block local drawer open");
            var staleSelect = request(menu, 15, RedstoneMenu.SELECT, 1, 0);
            control.range(0, 5, 11);
            drawer.action(player, staleSelect);
            require(drawer.selected() == 1, "Shared edits cannot block local face selection");
            player.containerMenu = player.inventoryMenu;
            machine.clearContent();
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        checkProcessorOutputs(helper);
    }

    private static void checkProcessorOutputs(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
        level.setBlock(pos, CompanionMod.DUAL_CRUSHER.block.get().defaultBlockState(), 3);
        var machine = (ProcessorEntity) level.getBlockEntity(pos);
        var control = machine.redstone;
        require(
            control.outputs().size() == machine.automation.groups().size() + 6,
            "Each processor contributes exactly three independent entries");
        String[] kinds = {"processing", "inactive", "progress"};
        for (int lane = 0; lane < 2; lane++)
            for (int kind = 0; kind < 3; kind++) {
                int face = lane * 3 + kind;
                String id = "processor/lane_" + lane + "/" + kinds[kind];
                int index = source(control, id);
                require(
                    index >= 0 && control.outputs().get(index).sprite().isPresent(),
                    "Each processor source has a stable ID and sprite");
                control.mode(face, RedstoneControl.Mode.EMIT);
                control.source(face, index);
            }
        tick(machine, 1);
        require(
            control.outgoing(0) == 0 && control.outgoing(1) == 15 && control.outgoing(2) == 0,
            "Idle processor emits 0 running, 15 inactive and 0 progress");
        machine.energy.setStored(100000);
        machine.items.insertItem(0, new ItemStack(Items.COPPER_ORE, 16), false);
        tick(machine, 15);
        require(
            control.outgoing(0) == 15
                && control.outgoing(1) == 0
                && control.outgoing(2) > 0
                && control.outgoing(3) == 15
                && control.outgoing(4) == 0,
            "Both lanes expose their own live running/progress values");
        int progress = control.outgoing(2);
        machine.toggle(0);
        tick(machine, 3);
        require(
            control.outgoing(0) == 0
                && control.outgoing(1) == 15
                && control.outgoing(2) == progress
                && control.outgoing(3) == 15
                && control.outgoing(4) == 0,
            "Pausing lane one retains progress without pausing lane two");
        machine.toggle(0);
        tick(machine, 2);
        machine.energy.setStored(0);
        progress = control.outgoing(2);
        tick(machine, 2);
        require(
            control.outgoing(0) == 0 && control.outgoing(1) == 15
                && control.outgoing(2) == progress,
            "An energy-stalled processor is inactive and retains progress");
        machine.energy.setStored(100000);
        tick(machine, 2);
        machine.setReservePercent(100);
        progress = control.outgoing(2);
        tick(machine, 2);
        require(
            control.outgoing(0) == 0 && control.outgoing(1) == 15
                && control.outgoing(2) == progress,
            "Host suspension clears running while retaining progress");
        machine.setReservePercent(0);
        tick(machine, 1);
        control.mode(5, RedstoneControl.Mode.READ);
        control.range(5, 0, 15);
        progress = control.outgoing(2);
        tick(machine, 2);
        require(
            control.outgoing(0) == 0 && control.outgoing(1) == 15
                && control.outgoing(2) == progress,
            "Redstone hold is reflected in processor outputs on the same tick");
        control.inverted(0, true);
        control.updateOutputs();
        require(control.outgoing(0) == 15, "Revert also applies to processor signals");
        var saved = control.save(level.registryAccess());
        var reordered = new java.util.ArrayList<RedstoneOutput>(control.outputs());
        java.util.Collections.reverse(reordered);
        var restored = new RedstoneControl(machine, reordered);
        restored.load(saved, level.registryAccess());
        require(
            restored.outputs().get(restored.source(0)).id().equals("processor/lane_0/processing")
                && restored.inverted(0),
            "Saved source ID survives output reordering");
        var old = saved.copy();
        old.remove("source_0");
        old.putString("group_0", "energy");
        restored.load(old, level.registryAccess());
        require(
            restored.outputs().get(restored.source(0)).id().equals("energy"),
            "Old inventory selections migrate to the source catalog");
        int[] calls = {0}, sourceValue = {99};
        var custom = new RedstoneOutput(
            "custom",
            net.minecraft.network.chat.Component.literal("Custom"),
            net.minecraft.network.chat.Component.literal("Custom output"),
            0x123456,
            java.util.Optional.empty(),
            () -> {
                calls[0]++;
                return sourceValue[0];
            });
        var discovered = new RedstoneControl(machine, List.of(new Object(), custom));
        require(discovered.outputs().size() == 1, "Non-output parts are ignored");
        for (int face = 0; face < 6; face++) {
            discovered.mode(face, RedstoneControl.Mode.EMIT);
            discovered.source(face, 0);
        }
        discovered.updateOutputs();
        require(
            calls[0] == 1 && discovered.outgoing(0) == 15,
            "Custom source is clamped and evaluated once for all faces");
        sourceValue[0] = 4;
        discovered.inverted(0, true);
        discovered.updateOutputs();
        require(
            calls[0] == 2 && discovered.outgoing(0) == 11 && discovered.outgoing(1) == 4,
            "Samples refresh every update and each face inverts the raw value independently");
        for (int face = 0; face < 6; face++)
            discovered.mode(face, RedstoneControl.Mode.IGNORE);
        discovered.updateOutputs();
        require(
            calls[0] == 2 && discovered.outgoing(0) == 0 && discovered.outgoing(1) == 0,
            "Unused sources are not sampled and old emissions are cleared");
        discovered.mode(1, RedstoneControl.Mode.EMIT);
        sourceValue[0] = -10;
        discovered.updateOutputs();
        require(
            calls[0] == 3 && discovered.outgoing(1) == 0,
            "Reenabled source refreshes its sample and clamps negative values");
        var missing = discovered.save(level.registryAccess());
        missing.putString("source_1", "removed_source");
        discovered.load(missing, level.registryAccess());
        discovered.inverted(1, true);
        discovered.updateOutputs();
        require(
            discovered.source(1) == -1 && calls[0] == 3 && discovered.outgoing(1) == 0,
            "Loading a missing source clears the cached index and keeps emission at zero");
        discovered.source(1, 0);
        discovered.migrate(0);
        require(discovered.source(1) == -1, "Migration clears cached source indexes");
        int[] scratch = new int[48];
        var snapshot = new RedstoneNetwork.State(1, 1, 0, 0, false, 0, false, scratch);
        scratch[2] = 15;
        require(snapshot.faces()[2] == 0,
            "Reusing a snapshot buffer cannot mutate an existing packet");
        boolean duplicateRejected = false;
        try {
            new RedstoneControl(machine, List.of(custom, custom));
        } catch (IllegalArgumentException expected) {
            duplicateRejected = true;
        }
        require(duplicateRejected, "Ambiguous duplicate source IDs are rejected");
        machine.clearContent();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos, CompanionMod.CRUSHER.block.get().defaultBlockState(), 3);
        machine = (ProcessorEntity) level.getBlockEntity(pos);
        control = machine.redstone;
        control.mode(0, RedstoneControl.Mode.EMIT);
        control.source(0, source(control, "processor/lane_0/progress"));
        machine.energy.setStored(100000);
        machine.items.insertItem(0, new ItemStack(Items.COPPER_ORE), false);
        int ticks = 0;
        do {
            tick(machine, 1);
        } while (control.outgoing(0) < 15 && ++ticks < 1000);
        require(control.outgoing(0) == 15, "Completion emits progress fifteen for its final tick");
        tick(machine, 1);
        require(control.outgoing(0) == 0, "Completed processor returns progress to zero when idle");
        machine.clearContent();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    private static int source(RedstoneControl control, String id) {
        for (int i = 0; i < control.outputs().size(); i++)
            if (control.outputs().get(i).id().equals(id))
                return i;
        return -1;
    }

    private static RedstoneNetwork.Action request(
        MachineMenu menu, int seq, int kind, int face, int value) {
        return new RedstoneNetwork.Action(
            menu.containerId,
            menu.widgetSession(),
            seq,
            menu.components().get(BuiltinMenuComponents.REDSTONE).revision(),
            kind,
            face,
            0,
            value);
    }

    private static void tick(ProcessorEntity machine, int count) {
        for (int i = 0; i < count; i++)
            machine.serverTick();
    }

    private static void require(boolean ok, String message) {
        if (!ok)
            throw new AssertionError(message);
    }
}
