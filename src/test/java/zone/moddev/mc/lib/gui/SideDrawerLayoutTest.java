package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.SideDrawerLayout;

class SideDrawerLayoutTest {
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
