package zone.moddev.mc.companion.test;

import java.math.BigInteger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.api.stats.Statistics;
import zone.moddev.mc.lib.api.stats.StatsNumbers;
import zone.moddev.mc.lib.api.stats.StatsProvider;
import zone.moddev.mc.lib.api.stats.StatsSamplingContext;
import zone.moddev.mc.lib.api.stats.StatsTimescale;
import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.client.StatsDrawer;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.gui.runtime.StatsMenuComponent;
import zone.moddev.mc.lib.gui.runtime.component.BuiltinMenuComponents;
import zone.moddev.mc.lib.gui.runtime.component.MenuComponentRegistry;

/**
 * A host-provided custom type and fourth timescale, through the production sampler and wire field.
 */
final class StatsExtensionClientSmoke {
    private static final StatsTimescale HOUR = new StatsTimescale(
        ResourceLocation.parse("test:hour"),
        72000,
        Component.literal("Per hour"),
        Component.literal("h"));
    private static final List<StatsTimescale> ALL = List.of(StatsTimescale.TICK,
        StatsTimescale.SECOND, StatsTimescale.MINUTE, HOUR);
    private static volatile List<StatsTimescale> timescales = ALL;
    private static int step, attempts;

    private record CustomValue(int amount) implements Statistics.Value {
        public Statistics.Sample sample(StatsSamplingContext context, Statistics.Sample previous) {
            var squared = BigInteger.valueOf(amount).pow(2);
            return format -> format
                .number(StatsNumbers.rate(squared, 1, format.ticks(), format.decimals()))
                .copy()
                .withStyle(ChatFormatting.DARK_PURPLE);
        }
    }

    private static final StatsProvider CUSTOM = () -> new Statistics(
        Component.literal("Custom sensor"),
        List.of(
            new Statistics.Row(
                "square",
                Component.literal("Squared load"),
                new CustomValue(7),
                Component.literal("units"),
                0,
                true)));

    static void install() {
        // Inject only the test host through the existing component factory. Statistic types and
        // timescales themselves have no registration, exactly as in a real ScreenHost
        // implementation.
        MenuComponentRegistry.GLOBAL.replace(
            BuiltinMenuComponents.STATS,
            20,
            c -> {
                var host = new ScreenHost() {
                    public net.neoforged.neoforge.items.IItemHandlerModifiable screenItems() {
                        return null;
                    }

                    public List<? extends StatsProvider> screenStats() {
                        var providers = new ArrayList<StatsProvider>(c.host().screenStats());
                        providers.add(CUSTOM);
                        return providers;
                    }

                    public List<StatsTimescale> screenStatsTimescales() {
                        return timescales;
                    }
                };
                return new StatsMenuComponent(
                    new MenuComponentRegistry.Context(c.menu(), c.inventory(), host, c.client()));
            },
            StatsMenuComponent.PAGES);
    }

    static boolean run(MachineScreen screen) {
        if (++attempts > 240)
            throw new AssertionError("Statistics extension stuck at " + step);
        var pages = screen.getMenu().components().require(BuiltinMenuComponents.STATS).pages();
        var drawer = (StatsDrawer) screen.drawers().entries().stream()
            .filter(StatsDrawer.class::isInstance)
            .findFirst()
            .orElseThrow();
        if (step == 0) {
            if (pages.size() != 4 || screen.drawers().pending())
                return false;
            if (pages.get(3).lines().stream()
                .noneMatch(row -> row.getString().equals("Squared load: 3,528,000 units/h")))
                throw new AssertionError("Custom statistic was not sampled and synchronized");
            drawer.close();
            var tab = drawer.tabBounds();
            click(screen, tab, 0);
        } else if (step == 1) {
            requireSelected(drawer, StatsTimescale.TICK.id());
            clickUnit(screen, drawer, 0);
            requireSelected(drawer, StatsTimescale.SECOND.id());
            clickUnit(screen, drawer, 0);
            requireSelected(drawer, StatsTimescale.MINUTE.id());
            clickUnit(screen, drawer, 0);
            requireSelected(drawer, HOUR.id());
        } else if (step == 2) {
            capture("statistics-custom-hour.png");
            timescales = List.of(HOUR, StatsTimescale.MINUTE, StatsTimescale.TICK,
                StatsTimescale.SECOND);
        } else if (step == 3) {
            if (pages.isEmpty() || !pages.getFirst().id().equals(HOUR.id()))
                return false;
            requireSelected(drawer, HOUR.id());
            clickUnit(screen, drawer, 0);
            requireSelected(drawer, StatsTimescale.MINUTE.id());
            timescales = List.of(StatsTimescale.SECOND);
        } else if (step == 4) {
            if (pages.size() != 1)
                return false;
            requireSelected(drawer, StatsTimescale.SECOND.id());
            clickUnit(screen, drawer, 1);
            requireSelected(drawer, StatsTimescale.SECOND.id());
            timescales = List.of();
        } else if (step == 5) {
            if (!pages.isEmpty())
                return false;
            if (drawer.selectedPage().isPresent())
                throw new AssertionError("Removed page remained selected");
            clickUnit(screen, drawer, 0);
            capture("statistics-empty-pages.png");
            timescales = ALL;
        } else if (step == 6) {
            if (pages.size() != 4)
                return false;
            requireSelected(drawer, StatsTimescale.TICK.id());
            clickUnit(screen, drawer, 1);
            requireSelected(drawer, HOUR.id());
            clickUnit(screen, drawer, 0);
            requireSelected(drawer, StatsTimescale.TICK.id());
            screen.keyPressed(256, 0, 0);
            if (drawer.open() || Minecraft.getInstance().screen != screen)
                throw new AssertionError("Escape failed to close only statistics");
            return true;
        }
        step++;
        return false;
    }

    private static void requireSelected(StatsDrawer drawer, ResourceLocation id) {
        if (!drawer.selectedPage().equals(Optional.of(id)))
            throw new AssertionError("Wrong page: " + drawer.selectedPage() + ", expected " + id);
    }

    private static void clickUnit(MachineScreen screen, StatsDrawer drawer, int button) {
        drawer.selectedPage(); // refresh geometry and selection
        try {
            var method = StatsDrawer.class.getDeclaredMethod("unitBounds");
            method.setAccessible(true);
            click(screen, (Rect) method.invoke(drawer), button);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static void click(MachineScreen screen, Rect bounds, int button) {
        int x = bounds.x() + bounds.width() / 2, y = bounds.y() + bounds.height() / 2;
        if (!screen.mouseClicked(x, y, button))
            throw new AssertionError("Statistics click was ignored");
        screen.mouseReleased(x, y, button);
    }

    private static void capture(String name) {
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {
        });
    }
}
