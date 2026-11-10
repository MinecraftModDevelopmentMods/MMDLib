package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.layout.ContainerDrawerLayout;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.SideDrawerLayout;

class ContainerDrawerLayoutTest {
    private static ContainerDrawerLayout storage(Rect panel, int bottom, int slots) {
        return content(panel, bottom, slots, false, true);
    }

    private static ContainerDrawerLayout equipment(
        Rect panel, int bottom, int slots, boolean backpack) {
        return content(panel, bottom, slots, true, backpack);
    }

    private static ContainerDrawerLayout content(
        Rect panel, int bottom, int slots, boolean equipment, boolean backpack) {
        var type = net.minecraft.resources.ResourceLocation.parse("test:storage");
        var bag = new zone.moddev.mc.lib.gui.drawer.DrawerDefinition(
            type,
            "bag",
            zone.moddev.mc.lib.gui.drawer.DrawerPlacement.bottom(
                zone.moddev.mc.lib.gui.model.Edge.LEFT, 0));
        var gear = new zone.moddev.mc.lib.gui.drawer.DrawerDefinition(
            type,
            "gear",
            zone.moddev.mc.lib.gui.drawer.DrawerPlacement.bottom(
                zone.moddev.mc.lib.gui.model.Edge.LEFT, 10));
        var tabs = zone.moddev.mc.lib.gui.layout.DrawerTabs.place(
            java.util.List.of(bag, gear), panel, bottom, d -> d == bag ? backpack : equipment);
        return ContainerDrawerLayout.of(
            panel, bottom, slots, tabs.get(equipment ? gear : bag), equipment);
    }

    @Test
    void providerLayoutControlsColumnsCapacityAndCells() {
        var policy = new zone.moddev.mc.lib.gui.runtime.ContainerPolicy.Layout(100, 3, 4, 8, 26, 24,
            24, 20, 24);
        var panel = new Rect(240, 30, 200, 220);
        var tab = new Rect(218, 200, 22, 24);
        var layout = ContainerDrawerLayout.of(
            panel, panel.bottom(), 100, tab, policy, zone.moddev.mc.lib.gui.model.Edge.LEFT, 800);
        assertEquals(3, layout.columns());
        assertEquals(12, layout.pageSize());
        assertEquals(100, layout.body().width());
        assertEquals(new Rect(layout.body().x() + 32, layout.body().y() + 50, 20, 20),
            layout.cell(4));
        var cramped = ContainerDrawerLayout.of(
            new Rect(240, 30, 200, 50),
            80,
            100,
            tab,
            policy,
            zone.moddev.mc.lib.gui.model.Edge.LEFT,
            800);
        assertEquals(0, cramped.pageSize());
    }

    @Test
    void policiesCannotExceedWireBoundsOrSplitAlignedRows() {
        var layout = new zone.moddev.mc.lib.gui.runtime.ContainerPolicy.Layout(100, 3, 4, 8, 26, 24,
            24, 20, 24);
        assertThrows(
            IllegalArgumentException.class,
            () -> new zone.moddev.mc.lib.gui.runtime.ContainerPolicy.Settings(
                layout, 6, 2, java.util.Set.of(), true));
        assertThrows(
            IllegalArgumentException.class,
            () -> new zone.moddev.mc.lib.gui.runtime.ContainerPolicy.Layout(
                100, 3, 19, 8, 26, 24, 24, 20, 24));
    }

    @Test
    void equipmentPairsStayTogetherAndUseASeparateTab() {
        var panel = new Rect(220, 30, 200, 274);
        for (int slots : new int[]{2, 6, 34, 4096}) {
            var layout = equipment(panel, 400, slots, true);
            var backpack = storage(panel, 400, 27);
            assertEquals(backpack.body().width(), layout.body().width());
            assertEquals(backpack.body().x(), layout.body().x());
            assertEquals(panel.y() + SideDrawerLayout.MARGIN, layout.body().y());
            assertEquals(panel.height() - 2 * SideDrawerLayout.MARGIN, layout.body().height());
            // A tall text or automation drawer uses exactly the same vertical bounds.
            var content = SideDrawerLayout.body(
                panel,
                400,
                panel.right() + 22,
                panel.y() + SideDrawerLayout.MARGIN,
                150,
                Integer.MAX_VALUE);
            assertEquals(content.y(), layout.body().y());
            assertEquals(content.bottom(), layout.body().bottom());
            assertEquals(backpack.tab().y(), layout.tab().bottom());
            assertEquals(0, layout.pageSize() % 2);
            assertTrue(layout.pageSize() <= 54);
            for (int i = 0; i < layout.pageSize(); i += 2) {
                assertEquals(layout.cell(i).y(), layout.cell(i + 1).y());
                assertTrue(layout.cell(i).right() < layout.cell(i + 1).x());
                assertTrue(layout.cell(i + 1).right() <= layout.body().right() - 6);
                assertTrue(layout.cell(i).bottom() <= layout.previous().y());
            }
        }
        var alone = equipment(panel, 400, 2, false);
        assertEquals(panel.bottom() - SideDrawerLayout.MARGIN, alone.tab().bottom());
        assertEquals(0, equipment(new Rect(120, 30, 200, 274), 400, 10, false).pageSize());
    }

    @Test
    void storageStaysOnScreenAndLeftOfTheStationaryPanel() {
        for (int left : new int[]{150, 220, 350}) {
            for (int bottom : new int[]{100, 180, 300}) {
                for (int slots : new int[]{1, 27, 54, 120, 4096}) {
                    var panel = new Rect(left, 20, 200, 280);
                    var layout = storage(panel, bottom, slots);
                    assertEquals(panel.x(), layout.tab().right());
                    assertTrue(layout.tab().bottom() <= bottom);
                    assertTrue(layout.pageSize() >= 1 && layout.pageSize() <= 54);
                    assertEquals(6, layout.columns());
                    assertEquals(panel.y() + SideDrawerLayout.MARGIN, layout.body().y());
                    assertEquals(layout.body().y(), layout.rail().y());
                    assertEquals(layout.body().height(), layout.rail().height());
                    assertTrue(layout.body().x() >= 4 && layout.body().y() >= 4);
                    assertEquals(layout.tab().x(), layout.body().right());
                    assertTrue(layout.previous().right() <= layout.next().x());
                    assertEquals(
                        Math.min(panel.bottom() - SideDrawerLayout.MARGIN, bottom),
                        layout.body().bottom());
                    for (int i = 0; i < layout.pageSize(); i++) {
                        var cell = layout.cell(i);
                        assertTrue(cell.x() >= layout.body().x() + 4);
                        assertTrue(cell.right() <= layout.body().right() - 4);
                        assertTrue(cell.bottom() <= layout.previous().y());
                    }
                }
            }
        }
    }

    @Test
    void crampedWindowsKeepTheTabWithoutOffscreenSlots() {
        for (var panel : new Rect[]{
            new Rect(30, 10, 200, 180), new Rect(120, 10, 200, 180), new Rect(200, 4, 200, 40)
        }) {
            var layout = storage(panel, panel.bottom(), 100);
            assertEquals(0, layout.pageSize());
            assertEquals(0, layout.body().width());
            assertTrue(layout.tab().x() >= 0);
        }
    }
}
