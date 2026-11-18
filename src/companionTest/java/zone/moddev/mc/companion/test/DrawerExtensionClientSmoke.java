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
import zone.moddev.mc.lib.gui.client.WorkingAreaDisplay;
import zone.moddev.mc.lib.gui.drawer.DrawerDefinition;
import zone.moddev.mc.lib.gui.drawer.DrawerPlacement;
import zone.moddev.mc.lib.gui.drawer.DrawerRegistry;
import zone.moddev.mc.lib.gui.model.Edge;

/**
 * A mod drawer receives the generic hooks and supplies its own appearance and
 * local behavior.
 */
final class DrawerExtensionClientSmoke {
    private static Probe probe;
    private static boolean done;
    private static int upgradeStep;

    private static final ResourceLocation TYPE = ResourceLocation
        .parse("test:drawer");

    static void install() {
        DrawerRegistry.GLOBAL.register(TYPE,
            context -> context.host() == null
                ? List.of()
                : List.of(
                    new DrawerDefinition(TYPE, "test:drawer",
                        DrawerPlacement.top(Edge.LEFT, 50)),
                    new DrawerDefinition(TYPE, "test:right",
                        DrawerPlacement.top(Edge.RIGHT, 5)),
                    new DrawerDefinition(TYPE, "test:above_upgrades",
                        DrawerPlacement.top(Edge.RIGHT, -20))));
        DrawerFactories.GLOBAL.register(TYPE, Probe::new);
        installUpgrades();
    }

    private static void installUpgrades() {
        var id = ResourceLocation.parse("mmdlib_companion:crusher");
        var original = zone.moddev.mc.lib.gui.Screens.get(id).orElseThrow()
            .screen();
        String xml = """
            <screen xmlns="https://moddev.zone/mmdlib/screen" title="test"
                    block-entity="zone.moddev.mc.companion.ProcessorEntity">
              <vbox><spacer/></vbox>
              <upgrades-drawer id="layout_upgrades">
                <vbox padding="6"><label text="test.upgrades" text-fallback="Upgrades"/></vbox>
              </upgrades-drawer>
            </screen>
            """;
        try {
            var fixture = zone.moddev.mc.lib.gui.load.ScreenReader.read(id,
                "drawer-layout-fixture.xml", new java.io.ByteArrayInputStream(
                    xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            var tabs = new java.util.ArrayList<>(original.tabs());
            tabs.addAll(fixture.tabs());
            var replacement = new zone.moddev.mc.lib.gui.model.Screen(
                original.id(), original.source(), original.title(),
                original.blockEntity(), original.playerInventory(),
                original.body(), tabs);
            var screens = new java.util.HashMap<>(
                zone.moddev.mc.lib.gui.Screens.all());
            screens.put(id,
                zone.moddev.mc.lib.gui.load.ScreenBinder.bind(replacement,
                    DrawerExtensionClientSmoke.class.getClassLoader()));
            zone.moddev.mc.lib.gui.Screens.install(screens);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static boolean upgrades(MachineScreen screen) {
        if (upgradeStep == 3)
            return true;
        var drawer = (zone.moddev.mc.lib.gui.client.UpgradesDrawer) screen
            .drawers().entries().stream()
            .filter(
                zone.moddev.mc.lib.gui.client.UpgradesDrawer.class::isInstance)
            .findFirst().orElseThrow();
        if (drawer.pending())
            return false;
        if (upgradeStep == 1) {
            if (!drawer.open())
                throw new AssertionError("Upgrade drawer failed to open");
            var body = drawer.bodyBounds();
            int top = zone.moddev.mc.lib.gui.layout.SideDrawerLayout
                .top(screen.panelBounds());
            var element = (zone.moddev.mc.lib.gui.model.containers.UpgradesDrawerElement) screen
                .getMenu().bound.screen().find("layout_upgrades").orElseThrow();
            var content = screen.bounds(element.children().getFirst());
            if (body.y() != top || content.x() != body.x() + 6
                || content.y() != top + 6 || drawer.tabBounds().y() <= top
                || body.bottom() < drawer.tabBounds().bottom())
                throw new AssertionError(
                    "Upgrade body, children and rail disagree after tab reordering");
            screen.resize(net.minecraft.client.Minecraft.getInstance(),
                screen.width, screen.height);
            if (!body.equals(drawer.bodyBounds()))
                throw new AssertionError(
                    "Upgrade geometry changed on equal resize");
        } else if (upgradeStep == 2) {
            if (drawer.open())
                throw new AssertionError("Upgrade drawer failed to close");
            upgradeStep = 3;
            return true;
        }
        var tab = drawer.tabBounds();
        screen.mouseClicked(tab.x() + 5, tab.y() + 5, 0);
        screen.mouseReleased(tab.x() + 5, tab.y() + 5, 0);
        upgradeStep++;
        return false;
    }

    static boolean run(MachineScreen screen) {
        if (done)
            return true;
        if (!upgrades(screen))
            return false;
        if (probe == null) {
            workingAreas(screen);
            probe = (Probe) screen.drawers().entries().stream()
                .filter(d -> d.id().equals("test:drawer")).findFirst()
                .orElseThrow();
            probe.verifyContentBounds();
            var right = screen.drawers().entries().stream()
                .filter(d -> d.id().equals("test:right")).findFirst()
                .orElseThrow();
            var redstone = screen.drawers().entries().stream()
                .filter(RedstoneDrawer.class::isInstance).findFirst()
                .orElseThrow();
            var sides = screen.drawers().entries().stream()
                .filter(SideConfigDrawer.class::isInstance).findFirst()
                .orElseThrow();
            if (right.tabBounds().y() < sides.tabBounds().bottom()
                || right.tabBounds().bottom() > redstone.tabBounds().y())
                throw new AssertionError(
                    "Registered drawer must reserve space between built-ins");
            var before = right.tabBounds();
            screen.resize(net.minecraft.client.Minecraft.getInstance(),
                screen.width, screen.height);
            if (!before.equals(right.tabBounds()))
                throw new AssertionError(
                    "Drawer placement changed on equal resize");
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
        if (!probe.open || !probe.dragged || !probe.released || !probe.scrolled
            || !probe.typed)
            throw new AssertionError("Generic drawer input dispatch");
        var stats = screen.drawers().entries().stream()
            .filter(StatsDrawer.class::isInstance).findFirst().orElseThrow();
        var statsTab = stats.tabBounds();
        screen.mouseClicked(statsTab.x() + 5, statsTab.y() + 5, 0);
        screen.mouseReleased(statsTab.x() + 5, statsTab.y() + 5, 0);
        if (probe.open || !stats.open())
            throw new AssertionError(
                "Opening a sibling closes the custom drawer");
        screen.keyPressed(256, 0, 0);
        probe.enabled = false;
        done = true;
        return true;
    }

    private static void workingAreas(MachineScreen screen) {
        var machine = screen.getMenu().blockEntity;
        var first = new net.minecraft.nbt.CompoundTag();
        first.putString("dimension",
            machine.getLevel().dimension().location().toString());
        first.putLong("origin", machine.getBlockPos().asLong());
        first.putString("processor", "first");
        first.putBoolean("available", true);
        first.putDouble("maxX", 1);
        first.putDouble("maxY", 1);
        first.putDouble("maxZ", 1);
        var second = first.copy();
        second.putString("processor", "second");
        second.putDouble("maxX", 4);
        WorkingAreaDisplay.toggle(first);
        if (!WorkingAreaDisplay.visible(first)
            || WorkingAreaDisplay.visible(second))
            throw new AssertionError(
                "Area toggles must distinguish processors on one machine");
        WorkingAreaDisplay.toggle(second);
        var unavailable = new net.minecraft.nbt.CompoundTag();
        unavailable.putString("dimension", first.getString("dimension"));
        unavailable.putLong("origin", first.getLong("origin"));
        unavailable.putString("processor", "first");
        unavailable.putBoolean("available", false);
        WorkingAreaDisplay.update(unavailable);
        if (WorkingAreaDisplay.visible(first)
            || !WorkingAreaDisplay.visible(second))
            throw new AssertionError(
                "Unavailable area must clear only its own outline");
        WorkingAreaDisplay.toggle(unavailable);
        WorkingAreaDisplay.update(first);
        if (WorkingAreaDisplay.visible(first))
            throw new AssertionError(
                "Unavailable area cannot be enabled or silently restored");
        WorkingAreaDisplay.toggle(first);
        WorkingAreaDisplay.toggle(second);
        if (!WorkingAreaDisplay.visible(first)
            || WorkingAreaDisplay.visible(second))
            throw new AssertionError(
                "Toggling one processor must preserve the other outline");
        WorkingAreaDisplay.toggle(first);
    }

    static zone.moddev.mc.lib.gui.layout.ContainerDrawerLayout containerLayout(
        MachineScreen screen, boolean equipment) {
        return ((ContainerDrawer) screen.drawers().entries().stream()
            .filter(d -> d.id().equals(equipment ? "curios" : "backpack"))
            .findFirst().orElseThrow()).contentLayout();
    }

    private static final class Probe extends Drawer {
        private boolean enabled = true, open, dragged, released, scrolled,
            typed;
        private int frames;

        Probe(MachineScreen screen, DrawerDefinition definition) {
            super(screen, definition,
                IconSprite.gui(ResourceLocation.parse("mmdlib:icon_info")),
                0x92bcb4);
        }

        private boolean measuringContent;

        @Override
        public zone.moddev.mc.lib.gui.layout.Rect tabBounds() {
            if (measuringContent)
                throw new AssertionError(
                    "Content placement must not re-enter subclass tab layout");
            return super.tabBounds();
        }

        void verifyContentBounds() {
            measuringContent = true;
            try {
                var body = contentBounds(80, 100,
                    zone.moddev.mc.lib.gui.layout.SideDrawerLayout.HeightLimit.SCREEN);
                if (body.y() != zone.moddev.mc.lib.gui.layout.SideDrawerLayout
                    .top(screen.panelBounds()))
                    throw new AssertionError(
                        "Content stays aligned with the panel top");
            } finally {
                measuringContent = false;
            }
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

        public boolean drag(double x, double y, int button, double dx,
            double dy) {
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
