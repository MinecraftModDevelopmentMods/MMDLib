package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.SideDrawerLayout;

class SideDrawerLayoutTest {
    @Test
    void compactBodiesStillIncludeTheirActiveTabInTheSharedRail() {
        var panel = new Rect(200, 40, 198, 200);
        var tab = new Rect(398, 96, 22, 24);
        var body = SideDrawerLayout.topBody(panel, 450,
            zone.moddev.mc.lib.gui.model.Edge.RIGHT, tab, 80, 24,
            SideDrawerLayout.HeightLimit.SCREEN);
        assertEquals(44, body.y());
        assertEquals(tab.bottom(), body.bottom());
        assertEquals(body.height(), SideDrawerLayout.rail(tab, body).height());
    }

    @Test
    void standardBodiesStartAtPanelTopRegardlessOfTabOrder() {
        var panel = new Rect(200, 40, 198, 200);
        var upper = new Rect(398, 44, 22, 24);
        var lower = new Rect(398, 96, 22, 24);
        for (var tab : java.util.List.of(upper, lower)) {
            var body = SideDrawerLayout.topBody(panel, 450,
                zone.moddev.mc.lib.gui.model.Edge.RIGHT, tab, 112, 160,
                SideDrawerLayout.HeightLimit.SCREEN);
            assertEquals(new Rect(420, 44, 112, 160), body);
            assertTrue(
                SideDrawerLayout.rail(tab, body).contains(tab.x(), tab.y()));
        }
    }

    @Test
    void growingContentFitsBeforeItNeedsScrolling() {
        var panel = new Rect(200, 40, 198, 200);
        var full = SideDrawerLayout.body(panel, 450, 420, 44, 112, 300,
            SideDrawerLayout.HeightLimit.SCREEN);
        assertEquals(300, full.height());
        assertEquals(44, full.y());
        assertTrue(full.bottom() > panel.bottom());

        var clipped = SideDrawerLayout.body(panel, 280, 420, 44, 112, 300,
            SideDrawerLayout.HeightLimit.SCREEN);
        assertEquals(280, clipped.bottom());
        assertEquals(full.y(), clipped.y());

        var inventory = SideDrawerLayout.body(panel, 450, 58, 44, 120, 300);
        assertEquals(panel.bottom() - SideDrawerLayout.MARGIN,
            inventory.bottom());
    }
}
