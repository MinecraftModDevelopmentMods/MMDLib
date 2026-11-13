package zone.moddev.mc.lib.gui.drawer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.layout.ElementLayouts;
import zone.moddev.mc.lib.gui.model.Edge;
import zone.moddev.mc.lib.gui.model.containers.SideTabElement;
import zone.moddev.mc.lib.gui.model.elements.PopoutElement;

/** Built-in providers use the same registration path as mods. */
public final class BuiltinDrawers {
    public static final ResourceLocation SIDES = id("side_config"),
        REDSTONE = id("redstone"), STATS = id("stats"),
        BACKPACK = id("backpack"), CURIOS = id("curios"), POPOUT = id("popout"),
        SIDE_TAB = id("side_tab");

    private BuiltinDrawers() {
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath("mmdlib", name);
    }

    static void register(DrawerRegistry registry) {
        registry.register(SIDES,
            c -> c.enabled().contains(SIDES)
                || c.host() != null && c.host().screenAutomation() != null
                    ? List.of(new DrawerDefinition(SIDES, "automation",
                        DrawerPlacement.top(Edge.RIGHT, 0), Optional.empty(),
                        34, true))
                    : List.of());
        registry.register(REDSTONE,
            c -> c.enabled().contains(REDSTONE)
                || c.host() != null && c.host().screenRedstone() != null
                    ? List.of(new DrawerDefinition(REDSTONE, "redstone",
                        DrawerPlacement.top(Edge.RIGHT, 10)))
                    : List.of());
        registry.register(STATS,
            c -> c.host() == null || !c.host().screenStatsDrawer()
                ? List.of()
                : List.of(new DrawerDefinition(STATS, "stats",
                    DrawerPlacement.top(Edge.LEFT, 40))));
        registry.register(BACKPACK,
            c -> c.host() == null || !c.screen().playerInventory()
                ? List.of()
                : List.of(new DrawerDefinition(BACKPACK, "backpack",
                    DrawerPlacement.bottom(Edge.LEFT, 0))));
        registry.register(CURIOS,
            c -> c.host() == null || !c.screen().playerInventory()
                ? List.of()
                : List.of(new DrawerDefinition(CURIOS, "curios",
                    DrawerPlacement.bottom(Edge.LEFT, 10))));
        registry.register(POPOUT, c -> {
            var result = new ArrayList<DrawerDefinition>();
            for (var tab : c.screen().tabs())
                if (tab instanceof PopoutElement element)
                    result.add(new DrawerDefinition(POPOUT,
                        element.drawerId(c.screen()),
                        DrawerPlacement.top(element.edge(), 30),
                        Optional.of(element), 0, true));
            return result;
        });
        registry.register(SIDE_TAB, c -> {
            var result = new ArrayList<DrawerDefinition>();
            int index = 0;
            for (var tab : c.screen().tabs())
                if (tab instanceof SideTabElement element) {
                    var size = ElementLayouts.size(element, c.text());
                    result.add(new DrawerDefinition(SIDE_TAB,
                        "side-tab:" + index++,
                        new DrawerPlacement(element.edge(),
                            DrawerPlacement.Anchor.TOP, 20, size[0], size[1], 4,
                            2),
                        Optional.of(element), 0, false));
                }
            return result;
        });
    }
}
