package zone.moddev.mc.companion.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.layout.AutomationLayout;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.runtime.component.BuiltinMenuComponents;

/**
 * Real synced throughput rows, left drawer switching and the cycling timescale
 * control.
 */
final class StatsClientSmoke {
    private static final java.util.Map<MachineScreen, int[]> progress = new java.util.WeakHashMap<>();

    static boolean run(MachineScreen screen) {
        var state = progress.computeIfAbsent(screen, ignored -> new int[2]);
        int step = state[0];
        if (step > 11)
            return true;
        if (++state[1] < 2)
            return false;
        var menu = screen.getMenu();
        var panel = screen.panelBounds();
        var info = screen
            .bounds(menu.bound.screen().find("info").orElseThrow());
        var tab = new Rect(panel.x() - AutomationLayout.RAIL, info.bottom() + 2,
            AutomationLayout.RAIL, AutomationLayout.TAB_HEIGHT);
        String name = menu.bound.screen().id().getPath();
        var mc = Minecraft.getInstance();
        switch (step) {
            case 0 -> {
                if (menu.components().require(BuiltinMenuComponents.STATS)
                    .pages().isEmpty()
                    || menu.components().require(BuiltinMenuComponents.STATS)
                        .pages().get(0).lines().isEmpty()
                    || menu.components().get(BuiltinMenuComponents.BACKPACK)
                        .pending()
                    || menu.components().get(BuiltinMenuComponents.CURIOS)
                        .pending())
                    return false;
                int processors = name.contains("dual") ? 2 : 1;
                boolean consuming = menu.components()
                    .require(BuiltinMenuComponents.STATS).pages().get(0).lines()
                    .stream()
                    .anyMatch(c -> c.getString().startsWith("RF used:"));
                if (menu.components().require(BuiltinMenuComponents.STATS)
                    .pages().get(0).lines()
                    .size() != (consuming ? 5 : 3) + 4 * processors)
                    throw new AssertionError(
                        "Statistics should contain directional energy rows, conditional usage and two rows"
                            + " under each processor header");
                if (menu.components().require(BuiltinMenuComponents.STATS)
                    .pages().get(0).lines().stream()
                    .filter(c -> c.getString().contains("cycles/t")
                        || c.getString().contains("Max cycles: No recipe"))
                    .count() != 2 * processors)
                    throw new AssertionError(
                        "Each processor must have an independent cycle rate");
                click(screen, info.x() + 5, info.y() + 5, 0);
            }
            case 1 -> {
                // Opening Info can close a restored equipment drawer through a
                // server round trip.
                if (menu.components().get(BuiltinMenuComponents.BACKPACK)
                    .pending()
                    || menu.components().get(BuiltinMenuComponents.CURIOS)
                        .pending())
                    return false;
                click(screen, tab.x() + 5, tab.y() + 5, 0);
            }
            case 2 -> {
                requireUnit(screen, 0);
                if (screen.exclusions().stream()
                    .noneMatch(r -> r.right() == tab.x() && r.width() == 160))
                    throw new AssertionError(
                        "Statistics must expand to the left, under Info: "
                            + name + " " + screen.exclusions());
                Screenshot.grab(mc.gameDirectory, name + "-stats-tick.png",
                    mc.getMainRenderTarget(), m -> {
                    });
            }
            case 3 -> {
                clickUnit(screen, 0);
                requireUnit(screen, 1);
            }
            case 4 -> Screenshot.grab(mc.gameDirectory,
                name + "-stats-second.png", mc.getMainRenderTarget(), m -> {
                });
            case 5 -> {
                clickUnit(screen, 0);
                requireUnit(screen, 2);
            }
            case 6 -> Screenshot.grab(mc.gameDirectory,
                name + "-stats-minute.png", mc.getMainRenderTarget(), m -> {
                });
            case 7 -> {
                clickUnit(screen, 0);
                requireUnit(screen, 0);
                clickUnit(screen, 1);
                requireUnit(screen, 2);
            }
            case 8 -> Screenshot.grab(mc.gameDirectory,
                name + "-stats-reverse.png", mc.getMainRenderTarget(), m -> {
                });
            case 9 -> click(screen, info.x() + 5, info.y() + 5, 0);
            case 10 -> {
                if (screen.exclusions().stream()
                    .anyMatch(r -> r.right() == tab.x() && r.width() == 160))
                    throw new AssertionError(
                        "Info replaces Statistics on the left");
                click(screen, tab.x() + 5, tab.y() + 5, 0);
            }
            case 11 -> {
                screen.keyPressed(256, 0, 0);
                if (mc.screen != screen)
                    throw new AssertionError(
                        "Escape closes Statistics before the machine");
            }
        }
        state[0]++;
        state[1] = 0;
        return false;
    }

    private static void clickUnit(MachineScreen screen, int button) {
        try {
            var drawer = screen.drawers().entries().stream()
                .filter(
                    zone.moddev.mc.lib.gui.client.StatsDrawer.class::isInstance)
                .findFirst().orElseThrow();
            var layout = drawer.getClass().getDeclaredMethod("layout");
            layout.setAccessible(true);
            layout.invoke(drawer);
            var bounds = drawer.getClass().getDeclaredMethod("unitBounds");
            bounds.setAccessible(true);
            var rect = (Rect) bounds.invoke(drawer);
            click(screen, rect.x() + rect.width() / 2,
                rect.y() + rect.height() / 2, button);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static void requireUnit(MachineScreen screen, int expected) {
        var drawer = (zone.moddev.mc.lib.gui.client.StatsDrawer) screen
            .drawers().entries().stream()
            .filter(zone.moddev.mc.lib.gui.client.StatsDrawer.class::isInstance)
            .findFirst().orElseThrow();
        var selected = drawer.selectedPage();
        var expectedId = zone.moddev.mc.lib.api.stats.StatsTimescale.DEFAULTS
            .get(expected).id();
        if (!selected.equals(java.util.Optional.of(expectedId)))
            throw new AssertionError("Wrong statistics page: " + selected
                + ", expected " + expectedId);
    }

    private static void click(MachineScreen screen, int x, int y, int button) {
        screen.mouseClicked(x, y, button);
        screen.mouseReleased(x, y, button);
    }
}
