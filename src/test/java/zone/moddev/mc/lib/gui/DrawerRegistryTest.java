package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.client.DrawerFactories;
import zone.moddev.mc.lib.gui.drawer.DrawerDefinition;
import zone.moddev.mc.lib.gui.drawer.DrawerPlacement;
import zone.moddev.mc.lib.gui.drawer.DrawerRegistry;
import zone.moddev.mc.lib.gui.layout.DrawerTabs;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.ScreenLayout;
import zone.moddev.mc.lib.gui.layout.SideDrawerLayout;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Edge;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.PopoutKind;
import zone.moddev.mc.lib.gui.model.Screen;
import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.model.containers.PlayerInventoryElement;
import zone.moddev.mc.lib.gui.model.containers.SideTabElement;
import zone.moddev.mc.lib.gui.model.elements.PopoutElement;
import zone.moddev.mc.lib.gui.sync.ElementStateView;

class DrawerRegistryTest {
    static final ResourceLocation TYPE = ResourceLocation.parse("test:drawer");

    private static Screen screen(List<Element> tabs) {
        return new Screen(ResourceLocation.parse("test:screen"), "test.xml",
            new Text("test:title", Optional.empty()), "java.lang.Object", true,
            List.of(), tabs);
    }

    private static DrawerRegistry.Context context(Screen screen) {
        return new DrawerRegistry.Context(screen, TextMeasure.NONE, null);
    }

    private static DrawerDefinition definition(String id,
        DrawerPlacement placement) {
        return new DrawerDefinition(TYPE, id, placement);
    }

    @Test
    void providersAreExplicitReplaceableAndRejectDuplicateIdentities() {
        var registry = new DrawerRegistry();
        var context = context(screen(List.of()));
        var left = definition("left", DrawerPlacement.top(Edge.LEFT, 10));
        registry.register(TYPE, c -> List.of(left));
        assertEquals(List.of(left), registry.resolve(context));
        assertThrows(IllegalArgumentException.class,
            () -> registry.register(TYPE, c -> List.of()));
        registry.replace(TYPE, c -> List.of());
        assertTrue(registry.resolve(context).isEmpty());
        registry.replace(TYPE, c -> List.of(left, left));
        assertThrows(IllegalArgumentException.class,
            () -> registry.resolve(context));
        assertThrows(IllegalArgumentException.class, () -> registry
            .replace(ResourceLocation.parse("test:missing"), c -> List.of()));
        registry.replace(TYPE,
            c -> List
                .of(new DrawerDefinition(ResourceLocation.parse("other:type"),
                    "wrong", left.placement())));
        assertThrows(IllegalArgumentException.class,
            () -> registry.resolve(context));
    }

    @Test
    void deterministicOrderingDoesNotDependOnModRegistrationOrder() {
        var first = new DrawerRegistry();
        var second = new DrawerRegistry();
        var other = ResourceLocation.parse("test:other");
        var a = definition("a", DrawerPlacement.top(Edge.RIGHT, 5));
        var b = new DrawerDefinition(other, "b",
            DrawerPlacement.top(Edge.RIGHT, 5));
        first.register(TYPE, c -> List.of(a));
        first.register(other, c -> List.of(b));
        second.register(other, c -> List.of(b));
        second.register(TYPE, c -> List.of(a));
        assertEquals(first.resolve(context(screen(List.of()))),
            second.resolve(context(screen(List.of()))));
    }

    @Test
    void tabPackingHandlesBothEdgesAnchorsAndUnavailableBottomTabs() {
        var top = definition("top", DrawerPlacement.top(Edge.LEFT, 0));
        var next = definition("next", DrawerPlacement.top(Edge.LEFT, 5));
        var bottom = definition("bottom", DrawerPlacement.bottom(Edge.LEFT, 0));
        var above = definition("above", DrawerPlacement.bottom(Edge.LEFT, 10));
        var right = definition("right", DrawerPlacement.top(Edge.RIGHT, 0));
        var entries = List.of(above, next, right, top, bottom);
        var panel = new Rect(100, 20, 180, 160);
        var placed = DrawerTabs.place(entries, panel, 176, d -> true);
        assertEquals(panel.x(), placed.get(top).right());
        assertEquals(panel.right(), placed.get(right).x());
        assertEquals(placed.get(top).bottom() + 2, placed.get(next).y());
        assertEquals(176, placed.get(bottom).bottom());
        assertEquals(placed.get(bottom).y(), placed.get(above).bottom());
        var absent = DrawerTabs.place(entries, panel, 176, d -> d != bottom);
        assertEquals(placed.get(bottom), absent.get(above));
        assertEquals(placed.get(top), absent.get(top));
        var small = DrawerTabs.place(entries, panel, 50, d -> true);
        assertTrue(small.get(above).y() >= small.get(next).bottom());
    }

    @Test
    void minimumHeightIncludesTheDeclaredGapBetweenAnchors() {
        var top = definition("top", new DrawerPlacement(Edge.LEFT,
            DrawerPlacement.Anchor.TOP, 0, 22, 24, 0, 17));
        var bottom = definition("bottom", DrawerPlacement.bottom(Edge.LEFT, 0));
        var entries = List.of(top, bottom);
        int height = DrawerTabs.minimumHeight(entries);
        var placed = DrawerTabs.place(entries, new Rect(100, 0, 180, height),
            height, d -> true);
        assertEquals(placed.get(top).bottom() + 17, placed.get(bottom).y());
        assertEquals(height - SideDrawerLayout.MARGIN,
            placed.get(bottom).bottom());
    }

    @Test
    void registryLayoutPositionsXmlTabsWithoutTypeChecksInScreenLayout() {
        var popout = new PopoutElement(Common.empty(), PopoutKind.INFO,
            Optional.empty(), Edge.RIGHT,
            Optional.of(new Text("test:info", Optional.empty())));
        var side = new SideTabElement(Common.empty(), Edge.LEFT, List.of());
        var screen = screen(List.of(popout, side));
        var registry = DrawerRegistry.builtins();
        var mod = definition("mod", new DrawerPlacement(Edge.RIGHT,
            DrawerPlacement.Anchor.TOP, 25, 30, 45, 0, 3));
        registry.register(TYPE, c -> List.of(mod));
        var definitions = registry.resolve(context(screen));
        var layout = ScreenLayout.of(screen, TextMeasure.NONE, definitions,
            ElementStateView.DEFAULTS);
        assertEquals(4 + 45 + 3, layout.bounds(popout).y());
        assertEquals(4, layout.bounds(side).right());
        assertTrue(
            layout.panel().height() >= DrawerTabs.minimumHeight(definitions));
        var missing = new DrawerRegistry();
        assertThrows(IllegalArgumentException.class,
            () -> missing.resolve(context(screen)));
    }

    @Test
    void arbitraryModElementCanOwnAnEdgeTabThroughAProvider() {
        record ModTab(Common common) implements Element {
            public int[] measure(TextMeasure text) {
                return new int[]{18, 30};
            }
        }
        var tab = new ModTab(Common.empty());
        var screen = screen(List.of(tab));
        var registry = new DrawerRegistry();
        registry.register(TYPE,
            c -> List.of(new DrawerDefinition(
                TYPE, "custom", new DrawerPlacement(Edge.LEFT,
                    DrawerPlacement.Anchor.TOP, 0, 18, 30, 1, 2),
                Optional.of(tab), 0, true)));
        var layout = ScreenLayout.of(screen, TextMeasure.NONE,
            registry.resolve(context(screen)), ElementStateView.DEFAULTS);
        assertEquals(new Rect(-17, 4, 18, 30), layout.bounds(tab));
    }

    @Test
    void clientFactoriesAreRequiredOnlyForInteractiveDefinitions() {
        var factories = new DrawerFactories();
        var interactive = definition("custom",
            DrawerPlacement.top(Edge.LEFT, 0));
        assertThrows(IllegalArgumentException.class,
            () -> factories.validate(List.of(interactive)));
        factories.register(TYPE, (screen, definition) -> null);
        factories.validate(List.of(interactive));
        assertThrows(IllegalArgumentException.class,
            () -> factories.register(TYPE, (screen, definition) -> null));
        factories.replace(TYPE, (screen, definition) -> null);
        assertThrows(IllegalArgumentException.class,
            () -> factories.replace(ResourceLocation.parse("test:absent"),
                (screen, definition) -> null));
        new DrawerFactories().validate(List.of(new DrawerDefinition(TYPE,
            "layout", interactive.placement(), Optional.empty(), 0, false)));
    }

    @Test
    void invalidGeometryIsRejectedAndToolbarSpaceComesFromDefinitions() {
        assertThrows(IllegalArgumentException.class,
            () -> new DrawerPlacement(Edge.LEFT, DrawerPlacement.Anchor.TOP, 0,
                0, 24, 0, 2));
        var definition = new DrawerDefinition(TYPE, "custom",
            DrawerPlacement.top(Edge.RIGHT, 0), Optional.empty(), 34, true);
        var layout = ScreenLayout.of(screen(List.of()), TextMeasure.NONE,
            List.of(definition), ElementStateView.DEFAULTS);
        assertTrue(layout.region(PlayerInventoryElement.TOOLBAR).orElseThrow()
            .width() >= 34);
        assertTrue(ScreenLayout
            .of(screen(List.of()), TextMeasure.NONE, List.of(),
                ElementStateView.DEFAULTS)
            .region(PlayerInventoryElement.TOOLBAR).isEmpty());
    }
}
