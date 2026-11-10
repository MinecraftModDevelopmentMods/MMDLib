package zone.moddev.mc.companion.test;

import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.client.ContainerDrawer;
import zone.moddev.mc.lib.gui.client.Drawer;
import zone.moddev.mc.lib.gui.client.DrawerFactories;
import zone.moddev.mc.lib.gui.client.IconSprite;
import zone.moddev.mc.lib.gui.client.MachineScreen;
import zone.moddev.mc.lib.gui.client.RedstoneDrawer;
import zone.moddev.mc.lib.gui.client.SideConfigDrawer;
import zone.moddev.mc.lib.gui.client.StatsDrawer;
import zone.moddev.mc.lib.gui.drawer.DrawerDefinition;
import zone.moddev.mc.lib.gui.drawer.DrawerPlacement;
import zone.moddev.mc.lib.gui.drawer.DrawerRegistry;
import zone.moddev.mc.lib.gui.model.Edge;

/** A mod drawer receives the generic hooks and supplies its own appearance and local behavior. */
final class DrawerExtensionClientSmoke {
    private static Probe probe;
    private static boolean done;

    private static final ResourceLocation TYPE = ResourceLocation.parse("test:drawer");

    static void install() {
        DrawerRegistry.GLOBAL.register(
            TYPE,
            context -> context.host() == null
                ? List.of()
                : List.of(
                    new DrawerDefinition(TYPE, "test:drawer", DrawerPlacement.top(Edge.LEFT, 50)),
                    new DrawerDefinition(TYPE, "test:right", DrawerPlacement.top(Edge.RIGHT, 5))));
        DrawerFactories.GLOBAL.register(TYPE, Probe::new);
    }

    static boolean run(MachineScreen screen) {
        if (done)
            return true;
        if (probe == null) {
            probe = (Probe) screen.drawers().entries().stream()
                .filter(d -> d.id().equals("test:drawer"))
                .findFirst()
                .orElseThrow();
            var right = screen.drawers().entries().stream()
                .filter(d -> d.id().equals("test:right"))
                .findFirst()
                .orElseThrow();
            var redstone = screen.drawers().entries().stream()
                .filter(RedstoneDrawer.class::isInstance)
                .findFirst()
                .orElseThrow();
            var sides = screen.drawers().entries().stream()
                .filter(SideConfigDrawer.class::isInstance)
                .findFirst()
                .orElseThrow();
            if (right.tabBounds().y() < sides.tabBounds().bottom()
                || right.tabBounds().bottom() > redstone.tabBounds().y())
                throw new AssertionError("Registered drawer must reserve space between built-ins");
            var before = right.tabBounds();
            screen.resize(net.minecraft.client.Minecraft.getInstance(), screen.width,
                screen.height);
            if (!before.equals(right.tabBounds()))
                throw new AssertionError("Drawer placement changed on equal resize");
            return false;
        }
        if (probe.frames == 0)
            throw new AssertionError("Custom drawer was not rendered");
        if (probe.colour() != 0x92bcb4 || probe.iconSprite() == null)
            throw new AssertionError("Subclass appearance is retained");
        var tab = probe.tabBounds();
        screen.mouseClicked(tab.x() + 5, tab.y() + 5, 0);
        screen.mouseDragged(tab.x() + 6, tab.y() + 6, 0, 1, 1);
        screen.mouseReleased(tab.x() + 6, tab.y() + 6, 0);
        screen.mouseScrolled(tab.x() + 5, tab.y() + 5, 0, 1);
        screen.charTyped('x', 0);
        if (!probe.open || !probe.dragged || !probe.released || !probe.scrolled || !probe.typed)
            throw new AssertionError("Generic drawer input dispatch");
        var stats = screen.drawers().entries().stream()
            .filter(StatsDrawer.class::isInstance)
            .findFirst()
            .orElseThrow();
        var statsTab = stats.tabBounds();
        screen.mouseClicked(statsTab.x() + 5, statsTab.y() + 5, 0);
        screen.mouseReleased(statsTab.x() + 5, statsTab.y() + 5, 0);
        if (probe.open || !stats.open())
            throw new AssertionError("Opening a sibling closes the custom drawer");
        screen.keyPressed(256, 0, 0);
        probe.enabled = false;
        done = true;
        return true;
    }

    static zone.moddev.mc.lib.gui.layout.ContainerDrawerLayout containerLayout(
        MachineScreen screen, boolean equipment) {
        return ((ContainerDrawer) screen.drawers().entries().stream()
            .filter(d -> d.id().equals(equipment ? "curios" : "backpack"))
            .findFirst()
            .orElseThrow())
            .contentLayout();
    }

    private static final class Probe extends Drawer {
        private boolean enabled = true, open, dragged, released, scrolled, typed;
        private int frames;

        Probe(MachineScreen screen, DrawerDefinition definition) {
            super(
                screen, definition, IconSprite.gui(ResourceLocation.parse("mmdlib:icon_info")),
                0x92bcb4);
        }

        public boolean available() {
            return enabled;
        }

        public boolean open() {
            return open;
        }

        public void close() {
            open = false;
        }

        public void closeLocal() {
            close();
        }

        public void render(GuiGraphics g, int x, int y) {
            frames++;
        }

        public boolean click(double x, double y, int button) {
            if (!tabContains(x, y))
                return false;
            openLocal();
            open = true;
            return true;
        }

        public boolean drag(double x, double y, int button, double dx, double dy) {
            return dragged = open;
        }

        public boolean release(double x, double y, int button) {
            return released = open;
        }

        public boolean scroll(double x, double y, double amount) {
            return scrolled = open;
        }

        public boolean character(char character, int modifiers) {
            return typed = open;
        }
    }
}
