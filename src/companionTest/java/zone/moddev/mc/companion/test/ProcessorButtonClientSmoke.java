package zone.moddev.mc.companion.test;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import zone.moddev.mc.companion.ProcessorEntity;
import zone.moddev.mc.lib.api.redstone.RedstoneControl;
import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.model.ProcessorButtonState;
import zone.moddev.mc.lib.gui.model.elements.ProcessorButtonElement;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.gui.runtime.component.BuiltinMenuComponents;

/**
 * Real clicks and server snapshots for both directions of manual/redstone
 * disagreement.
 */
final class ProcessorButtonClientSmoke {
    private static int step, attempts;
    private static volatile boolean ready;
    private static CompoundTag savedRedstone;
    private static boolean savedPause;
    private static int savedReserve;
    private static long savedEnergy;

    static boolean run(MachineScreen screen) {
        if (step > 7)
            return true;
        if (++attempts > 120)
            throw new AssertionError(
                "Processor button smoke stalled at step " + step);
        if (step > 0 && !ready)
            return false;
        var menu = screen.getMenu();
        var button = (ProcessorButtonElement) menu.bound.screen()
            .find("toggle_0").orElseThrow();
        if (!button.interactive())
            throw new AssertionError("Companion must use the generic control");
        var wireState = menu.value(button, false);
        var state = ProcessorButtonState.fromWire(wireState).orElse(null);
        switch (step) {
            case 0 -> server(machine -> {
                savedPause = machine.lanes.getFirst().paused();
                savedReserve = machine.getReservePercent();
                savedEnergy = machine.energy.stored();
                savedRedstone = machine.redstone
                    .save(machine.getLevel().registryAccess());
                machine.lanes.forEach(lane -> lane.reset());
                machine.items.setStackInSlot(0,
                    new ItemStack(Items.COPPER_ORE, 16));
                machine.items.setStackInSlot(1, ItemStack.EMPTY);
                machine.items.setStackInSlot(2, ItemStack.EMPTY);
                machine.setReservePercent(0);
                machine.energy.setStored(100000);
                for (int face = 0; face < 6; face++)
                    machine.redstone.mode(face, RedstoneControl.Mode.IGNORE);
                machine.features.setProcessorPaused("lane_0", false);
            });
            case 1 -> {
                if (state != ProcessorButtonState.PROCESSING
                    || menu.components().get(BuiltinMenuComponents.REDSTONE)
                        .watching())
                    return false;
                require(!button.conflict(menu.value(button, true)),
                    "No monitoring means no red dot");
                tooltip(screen, button, "Working",
                    "Click to pause and release reservations",
                    "Progress is kept");
                require(
                    button.iconFor(wireState).orElseThrow().getPath()
                        .equals("icon_processor_stop"),
                    "An actively processing machine shows Stop");
                capture("processor-enabled-no-watch.png");
                server(machine -> {
                    machine.redstone.mode(0, RedstoneControl.Mode.READ);
                    machine.redstone.range(0, 0, 15);
                    machine.redstone.outside(0, false);
                });
            }
            case 2 -> {
                if (!menu.components().get(BuiltinMenuComponents.REDSTONE)
                    .watching()
                    || !menu.components().get(BuiltinMenuComponents.REDSTONE)
                        .paused()
                    || state != ProcessorButtonState.HELD)
                    return false;
                require(
                    button.iconFor(wireState).orElseThrow().getPath()
                        .equals("icon_processor_stop"),
                    "A redstone-held processor retains work and shows Stop");
                require(button.conflict(menu.value(button, true)),
                    "Enabled processor conflicts with redstone hold");
                tooltip(screen, button, "Held by machine controls",
                    "Click to pause and release reservations",
                    "Progress is kept", "Redstone settings conflict:",
                    "Paused by redstone.");
                capture("processor-redstone-hold.png");
                click(screen, button);
            }
            case 3 -> {
                if (state != ProcessorButtonState.PAUSED)
                    return false;
                require(
                    menu.components().get(BuiltinMenuComponents.REDSTONE)
                        .paused() && !button.conflict(menu.value(button, true)),
                    "Both pause controls agree");
                tooltip(screen, button, "Paused", "Click to unpause");
                require(
                    button.iconFor(wireState).orElseThrow().getPath()
                        .equals("icon_processor_pause"),
                    "A manually paused processor shows Pause");
                capture("processor-paused-redstone-hold.png");
                server(machine -> machine.redstone.outside(0, true));
            }
            case 4 -> {
                if (menu.components().get(BuiltinMenuComponents.REDSTONE)
                    .paused())
                    return false;
                require(
                    state == ProcessorButtonState.PAUSED
                        && button.conflict(menu.value(button, true)),
                    "Clearing redstone preserves manual pause and shows the conflict");
                tooltip(screen, button, "Paused", "Click to unpause",
                    "Redstone settings conflict:",
                    "Manual pause takes priority.");
                capture("processor-manual-hold.png");
                click(screen, button);
            }
            case 5 -> {
                if (state != ProcessorButtonState.PROCESSING)
                    return false;
                require(
                    menu.components().get(BuiltinMenuComponents.REDSTONE)
                        .watching()
                        && !button.conflict(menu.value(button, true)),
                    "Both allow controls agree");
                capture("processor-redstone-run.png");
                server(machine -> machine.energy.setStored(0));
            }
            case 6 -> {
                if (state != ProcessorButtonState.STALLED)
                    return false;
                require(!button.conflict(menu.value(button, true)),
                    "Lack of power is not a manual/redstone disagreement");
                tooltip(screen, button, "Needs power",
                    "Click to pause and release reservations",
                    "Progress is kept");
                require(
                    button.iconFor(wireState).orElseThrow().getPath()
                        .equals("icon_processor_stop"),
                    "A stalled processor without power retains work and shows Stop");
                capture("processor-stalled-no-power.png");
                server(machine -> {
                    machine.energy.setStored(savedEnergy);
                    machine.setReservePercent(savedReserve);
                    machine.redstone.load(savedRedstone,
                        machine.getLevel().registryAccess());
                    machine.features.setProcessorPaused("lane_0", savedPause);
                });
            }
            case 7 -> {
                if (state == null
                    || (state == ProcessorButtonState.PAUSED) != savedPause)
                    return false;
            }
        }
        attempts = 0;
        step++;
        return false;
    }

    private static void tooltip(MachineScreen screen,
        ProcessorButtonElement button, String... expected) {
        var bounds = screen.bounds(button);
        var actual = screen.tooltipLines(button, bounds.x(), bounds.y())
            .stream().map(net.minecraft.network.chat.Component::getString)
            .toList();
        require(actual.equals(java.util.List.of(expected)),
            "Unexpected processor tooltip: " + actual);
    }

    private static void click(MachineScreen screen,
        ProcessorButtonElement button) {
        var bounds = screen.bounds(button);
        double x = bounds.x() + bounds.width() / 2.,
            y = bounds.y() + bounds.height() / 2.;
        require(screen.mouseClicked(x, y, 0),
            "Generic button accepts the real click");
        screen.mouseReleased(x, y, 0);
    }

    private static void server(Consumer<ProcessorEntity> action) {
        ready = false;
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> {
            try {
                var player = server.getPlayerList().getPlayers().getFirst();
                var menu = (MachineMenu) player.containerMenu;
                var machine = (ProcessorEntity) menu.blockEntity;
                action.accept(machine);
                machine.redstone.readInputs();
                menu.broadcastChanges();
                ready = true;
            } catch (Throwable error) {
                CompanionClientSmoke.fail(error);
            }
        });
    }

    private static void capture(String name) {
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(),
            message -> {
            });
    }

    private static void require(boolean value, String message) {
        if (!value)
            throw new AssertionError(message);
    }
}
