package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.drawer.DrawerDefinition;
import zone.moddev.mc.lib.gui.drawer.DrawerPlacement;
import zone.moddev.mc.lib.gui.layout.ContainerDrawerLayout;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.SideDrawerLayout;
import zone.moddev.mc.lib.gui.model.Edge;
import zone.moddev.mc.lib.gui.runtime.component.DrawerSession;
import zone.moddev.mc.lib.gui.runtime.component.DrawerSessions;
import zone.moddev.mc.lib.gui.runtime.component.MenuComponentKey;
import zone.moddev.mc.lib.gui.runtime.component.MenuComponentRegistry;

class MenuComponentsTest {
    private static final class Session implements DrawerSession {
        final ResourceLocation id;
        int closes;

        Session(String id) {
            this.id = ResourceLocation.parse(id);
        }

        public ResourceLocation drawerType() {
            return id;
        }

        public void closeServer() {
            closes++;
        }

        DrawerDefinition at(Edge edge) {
            return new DrawerDefinition(id, id.toString(), DrawerPlacement.top(edge, 0));
        }
    }

    @Test
    void registeredEdgesControlServerExclusionForModAndBuiltinSessions() {
        var source = new Session("test:custom");
        var backpack = new Session("mmdlib:backpack");
        var sides = new Session("mmdlib:side_config");
        var sessions = List.<DrawerSession>of(source, backpack, sides);
        DrawerSessions.activate(
            source,
            sessions,
            List.of(source.at(Edge.RIGHT), backpack.at(Edge.RIGHT), sides.at(Edge.LEFT)));
        assertEquals(0, source.closes);
        assertEquals(1, backpack.closes);
        assertEquals(0, sides.closes);
        DrawerSessions.activate(source, sessions, List.of(backpack.at(Edge.RIGHT)));
        assertEquals(1, backpack.closes, "An unregistered session cannot close registered drawers");
        assertThrows(
            IllegalArgumentException.class,
            () -> DrawerSessions.activate(
                source, sessions, List.of(source.at(Edge.LEFT), source.at(Edge.RIGHT))));
    }

    @Test
    void registryOrderAndFingerprintAreDeterministicAndReplacementIsExplicit() {
        var a = new MenuComponentKey<>(ResourceLocation.parse("test:a"), Session.class);
        var b = new MenuComponentKey<>(ResourceLocation.parse("test:b"), Session.class);
        var first = new MenuComponentRegistry();
        var second = new MenuComponentRegistry();
        first.register(b, 10, c -> new Session("test:b"));
        first.register(a, 0, c -> new Session("test:a"));
        second.register(a, 0, c -> new Session("test:a"));
        second.register(b, 10, c -> new Session("test:b"));
        assertEquals(first.fingerprint(), second.fingerprint());
        assertThrows(IllegalArgumentException.class, () -> first.register(a, 0, c -> null));
        assertThrows(
            IllegalArgumentException.class,
            () -> new MenuComponentRegistry().replace(a, 0, c -> null));
        first.replace(a, 20, c -> null);
        assertNotEquals(first.fingerprint(), second.fingerprint());
    }

    @Test
    void inventoryBodyCanMoveToTheRightAndStillFitsTheViewport() {
        var panel = new Rect(180, 20, 200, 250);
        var tab = new Rect(panel.right(), 230, 22, 22);
        var layout = ContainerDrawerLayout.of(panel, 300, 27, tab, false, Edge.RIGHT, 600);
        assertEquals(tab.right(), layout.body().x());
        assertTrue(layout.pageSize() > 0);
        assertTrue(layout.body().right() <= 596);
        assertEquals(
            0, ContainerDrawerLayout.of(panel, 300, 27, tab, false, Edge.RIGHT, 450).pageSize());
        assertEquals(120, SideDrawerLayout.bodyX(Edge.LEFT, new Rect(240, 20, 22, 22), 120));
    }
}
