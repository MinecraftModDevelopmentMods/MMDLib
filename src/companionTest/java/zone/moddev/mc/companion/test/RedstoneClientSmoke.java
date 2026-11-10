package zone.moddev.mc.companion.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import zone.moddev.mc.companion.ProcessorEntity;
import zone.moddev.mc.lib.api.redstone.RedstoneControl;
import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.layout.AutomationLayout;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.RedstoneLayout;
import zone.moddev.mc.lib.gui.runtime.component.BuiltinMenuComponents;

/** Real face controls, crossing range handles, output cycling and sibling exclusion. */
final class RedstoneClientSmoke {
    private static int step, reported = -1;
    private static volatile boolean ready;

    static boolean run(MachineScreen screen) {
        var mc = Minecraft.getInstance();
        var menu = screen.getMenu();
        var model = menu.components().get(BuiltinMenuComponents.REDSTONE);
        if (reported != step) {
            reported = step;
            zone.moddev.mc.lib.MMDLib.LOGGER.info("REDSTONE_DRAWER_STEP {}", step);
        }
        if (model == null)
            throw new AssertionError("Processor must expose redstone drawer");
        if (model.pending()
            || menu.components().get(BuiltinMenuComponents.AUTOMATION) != null
                && menu.components().get(BuiltinMenuComponents.AUTOMATION).pending())
            return false;
        var panel = screen.panelBounds();
        int x = panel.right() + AutomationLayout.RAIL, y = panel.y() + AutomationLayout.TOP;
        var tab = screen.drawers().entries().stream()
            .filter(zone.moddev.mc.lib.gui.client.RedstoneDrawer.class::isInstance)
            .findFirst()
            .orElseThrow()
            .tabBounds();
        switch (step) {
            case 0 -> {
                require(menu.bound.screen().find("redstone").isEmpty(),
                    "Main redstone button removed");
                if (!model.open()) {
                    click(screen, tab, 0);
                    for (int tick = 0; tick < 99; tick++)
                        model.tick();
                    require(model.pending(), "Redstone remains pending before timeout");
                    model.tick();
                    require(model.pending(), "Redstone timeout starts a resync request");
                }
            }
            case 1 -> {
                if (!model.open())
                    return false;
                require(
                    !menu.components().get(BuiltinMenuComponents.AUTOMATION).open(),
                    "Redstone excludes inventory drawer on right");
                click(screen, RedstoneLayout.face(model.selected()).offset(x, y), 0);
            }
            case 2 -> {
                require(model.mode(0) == RedstoneControl.Mode.READ, "Read mode synchronized");
                range(screen, x, y, false, 15, 6);
            }
            case 3 -> {
                require(model.lower(0) == 1 && model.upper(0) == 6, "Upper handle sets maximum");
                range(screen, x, y, true, 1, 10);
            }
            case 4 -> {
                require(model.lower(0) == 10 && model.upper(0) == 10,
                    "Lower crossing pushes upper right");
                Screenshot.grab(
                    mc.gameDirectory, "crusher-redstone-for.png", mc.getMainRenderTarget(), m -> {
                    });
                range(screen, x, y, false, 10, 4);
            }
            case 5 -> {
                require(model.lower(0) == 4 && model.upper(0) == 4,
                    "Upper crossing pushes lower left");
                click(screen, RedstoneLayout.intervalMode().offset(x, y), 0);
            }
            case 50 -> {
                require(
                    model.outside(0) && model.lower(0) == 4 && model.upper(0) == 4,
                    "Outside preserves equal handles");
                Screenshot.grab(
                    mc.gameDirectory, "crusher-redstone-except.png", mc.getMainRenderTarget(),
                    m -> {
                    });
                click(screen, RedstoneLayout.intervalMode().offset(x, y), 0);
            }
            case 51 -> {
                range(screen, x, y, false, 4, 10);
            }
            case 6 -> {
                require(model.lower(0) == 4 && model.upper(0) == 10, "Interval 4-10 synchronized");
                Screenshot.grab(
                    mc.gameDirectory, "crusher-redstone-between.png", mc.getMainRenderTarget(),
                    m -> {
                    });
                click(screen, RedstoneLayout.intervalMode().offset(x, y), 0);
            }
            case 7 -> {
                require(model.outside(0), "Outside toggle synchronized");
                Screenshot.grab(
                    mc.gameDirectory, "crusher-redstone-outside.png", mc.getMainRenderTarget(),
                    m -> {
                    });
                click(screen, RedstoneLayout.face(1).offset(x, y), 0);
            }
            case 8 -> {
                require(
                    model.selected() == 1 && model.mode(1) == RedstoneControl.Mode.IGNORE,
                    "First click selects without editing");
                click(screen, RedstoneLayout.face(1).offset(x, y), 1);
            }
            case 9 -> {
                require(model.mode(1) == RedstoneControl.Mode.EMIT, "Reverse cycle chooses output");
                require(model.source(1) == -1, "Output starts unconfigured");
                click(screen, RedstoneLayout.group().offset(x, y), 0);
            }
            case 10 -> {
                require(model.source(1) == 0, "First click selects first inventory");
                click(screen, RedstoneLayout.group().offset(x, y), 0);
            }
            case 11 -> {
                require(model.source(1) == 1, "Click cycles inventory");
                Screenshot.grab(
                    mc.gameDirectory, "crusher-redstone-output.png", mc.getMainRenderTarget(),
                    m -> {
                    });
                click(screen, RedstoneLayout.revert().offset(x, y), 0);
            }
            case 60 -> {
                require(model.inverted(1), "Revert icon enables output inversion");
                Screenshot.grab(
                    mc.gameDirectory, "crusher-redstone-reverted.png", mc.getMainRenderTarget(),
                    m -> {
                    });
                click(screen, RedstoneLayout.revert().offset(x, y), 0);
            }
            case 61 -> {
                require(!model.inverted(1), "Revert icon disables output inversion");
                click(screen, RedstoneLayout.group().offset(x, y), 1);
            }
            case 12 -> {
                require(model.source(1) == 0, "Right click cycles inventory backwards");
                click(screen, RedstoneLayout.group().offset(x, y), 0);
            }
            case 70 -> {
                if (!model.control
                    .outputs()
                    .get(model.source(1))
                    .id()
                    .equals("processor/lane_0/processing")) {
                    click(screen, RedstoneLayout.group().offset(x, y), 0);
                    return false;
                }
                require(
                    model.control.outputs().get(model.source(1)).sprite().isPresent(),
                    "Processor picker uses its supplied sprite");
                Screenshot.grab(
                    mc.gameDirectory, "crusher-redstone-processing.png", mc.getMainRenderTarget(),
                    m -> {
                    });
                click(screen, RedstoneLayout.group().offset(x, y), 0);
            }
            case 71 -> {
                require(
                    model.control.outputs().get(model.source(1)).id()
                        .equals("processor/lane_0/inactive"),
                    "Processor paused/idle entry follows running");
                Screenshot.grab(
                    mc.gameDirectory, "crusher-redstone-inactive.png", mc.getMainRenderTarget(),
                    m -> {
                    });
                click(screen, RedstoneLayout.group().offset(x, y), 0);
            }
            case 72 -> {
                require(
                    model.control.outputs().get(model.source(1)).id()
                        .equals("processor/lane_0/progress"),
                    "Processor progress is an independent picker entry");
                Screenshot.grab(
                    mc.gameDirectory, "crusher-redstone-progress.png", mc.getMainRenderTarget(),
                    m -> {
                    });
                click(screen, RedstoneLayout.face(0).offset(x, y), 0);
            }
            case 13 -> {
                require(
                    model.lower(0) == 4 && model.upper(0) == 10 && model.outside(0),
                    "Range stays on its face across selections");
                // Escape cancels an in-progress drag without applying it or closing the drawer.
                var bar = RedstoneLayout.bar().offset(x, y);
                screen.mouseClicked(RedstoneLayout.signalX(bar, 4), bar.y() - 3, 0);
                screen.mouseDragged(bar.right() + 20, bar.y() - 3, 0, 100, 0);
                screen.keyPressed(256, 0, 0);
                screen.mouseReleased(bar.right() + 20, bar.y() - 3, 0);
                require(
                    model.open() && !model.pending() && model.lower(0) == 4 && model.upper(0) == 10,
                    "Escape cancels range preview");
                click(
                    screen,
                    new Rect(panel.right(), y, AutomationLayout.RAIL, AutomationLayout.TAB_HEIGHT),
                    0);
            }
            case 14 -> {
                if (!menu.components().get(BuiltinMenuComponents.AUTOMATION).open() || model.open())
                    return false;
                screen.keyPressed(256, 0, 0);
                server(
                    player -> {
                        var machine = (ProcessorEntity) ((zone.moddev.mc.lib.gui.runtime.MachineMenu) player.containerMenu).blockEntity;
                        for (int face = 0; face < 6; face++)
                            machine.redstone.mode(face, RedstoneControl.Mode.IGNORE);
                        machine.redstone.tick();
                    });
            }
            default -> {
                return ready
                    && !model.open()
                    && !menu.components().get(BuiltinMenuComponents.AUTOMATION).open();
            }
        }
        step = step == 5
            ? 50
            : step == 51
                ? 6
                : step == 11 ? 60 : step == 61 ? 12 : step == 12 ? 70 : step == 72 ? 13 : step + 1;
        return false;
    }

    private static void range(MachineScreen screen, int x, int y, boolean lower, int from, int to) {
        var bar = RedstoneLayout.bar().offset(x, y);
        int mouseY = lower ? bar.y() - 3 : bar.bottom() + 3;
        int beforeLow = screen.getMenu().components().get(BuiltinMenuComponents.REDSTONE).lower(0),
            beforeHigh = screen.getMenu().components().get(BuiltinMenuComponents.REDSTONE).upper(0);
        screen.mouseClicked(RedstoneLayout.signalX(bar, from), mouseY, 0);
        screen.mouseDragged(RedstoneLayout.signalX(bar, to), mouseY, 0, (to - from) * 6, 0);
        require(
            screen.getMenu().components().get(BuiltinMenuComponents.REDSTONE).lower(0) == beforeLow
                && screen.getMenu().components().get(BuiltinMenuComponents.REDSTONE)
                    .upper(0) == beforeHigh,
            "Dragging previews without applying early");
        screen.mouseReleased(RedstoneLayout.signalX(bar, to), mouseY, 0);
    }

    private static void click(MachineScreen screen, Rect r, int button) {
        screen.mouseClicked(r.x() + 7, r.y() + 7, button);
        screen.mouseReleased(r.x() + 7, r.y() + 7, button);
    }

    private static void server(
        java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action) {
        ready = false;
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(
            () -> {
                try {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    action.accept(player);
                    player.containerMenu.broadcastChanges();
                    ready = true;
                } catch (Throwable error) {
                    CompanionClientSmoke.fail(error);
                }
            });
    }

    private static void require(boolean ok, String message) {
        if (!ok)
            throw new AssertionError(message);
    }
}
