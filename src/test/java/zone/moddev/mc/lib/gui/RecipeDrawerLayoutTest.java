package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.layout.RecipeDrawerLayout;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Edge;

class RecipeDrawerLayoutTest {
    @Test
    void backgroundIncludesTabRailWithoutOvershootingOverlappingPanels() {
        var tab = new Rect(200, 60, 22, 24);
        var body = new Rect(20, 34, 180, 166);
        assertEquals(new Rect(20, 34, 202, 166),
            zone.moddev.mc.lib.gui.layout.SideDrawerLayout.frame(tab, body));
        assertEquals(new Rect(200, 34, 22, 166),
            zone.moddev.mc.lib.gui.layout.SideDrawerLayout.rail(tab, body));
        var overlapping = new Rect(100, 34, 180, 166);
        assertEquals(overlapping, zone.moddev.mc.lib.gui.layout.SideDrawerLayout
            .frame(tab, overlapping));
    }
    @Test
    void narrowMarginsOverlapInsteadOfShrinkingRecipeRows() {
        var panel = new Rect(70, 30, 220, 190);
        var left = RecipeDrawerLayout.body(Edge.LEFT, new Rect(48, 54, 22, 24),
            panel, 360, 236, 168, 24);
        var right = RecipeDrawerLayout.body(Edge.RIGHT,
            new Rect(290, 54, 22, 24), panel, 360, 236, 168, 24);
        assertEquals(168, RecipeDrawerLayout.content(left).width());
        assertEquals(168, RecipeDrawerLayout.content(right).width());
        assertTrue(left.right() > panel.x());
        assertTrue(right.x() < panel.right());
        assertEquals(70, left.x());
        assertEquals(290, right.right());
        assertTrue(left.x() >= 4 && right.right() <= 356);
        assertEquals(panel.y() + 4, left.y());
        assertEquals(panel.bottom() - 4, left.bottom());
        assertEquals(panel.y() + 4, right.y());
        assertEquals(panel.bottom() - 4, right.bottom());
        assertEquals(5, RecipeDrawerLayout.pageSize(left, 24));
    }

    @Test
    void constrainedViewportsKeepContentOnOneSideOfTheRail() {
        for (int screenWidth : new int[]{180, 240, 360, 900})
            for (var edge : Edge.values()) {
                var panel = new Rect((screenWidth - 140) / 2, 30, 140, 190);
                var tab = new Rect(
                    edge == Edge.LEFT ? panel.x() - 22 : panel.right(), 54, 22,
                    24);
                var body = RecipeDrawerLayout.body(edge, tab, panel,
                    screenWidth, 236, 168, 24);
                var content = RecipeDrawerLayout.content(body);
                assertTrue(body.right() <= tab.x() || body.x() >= tab.right(),
                    "Recipe body must not cross the navigation rail");
                assertTrue(body.x() >= 4 && body.right() <= screenWidth - 4);
                assertTrue(content.width() >= 0);
            }
    }

    @Test
    void wideScreensUseTheSideMarginAndShortScreensPageCompleteRows() {
        var panel = new Rect(300, 30, 220, 190);
        var tab = new Rect(278, 54, 22, 24);
        var body = RecipeDrawerLayout.body(Edge.LEFT, tab, panel, 900, 150, 168,
            24);
        assertEquals(tab.x(), body.right());
        assertTrue(body.bottom() <= 150);
        assertEquals(2, RecipeDrawerLayout.pageSize(body, 24));
        var content = RecipeDrawerLayout.content(body);
        assertTrue(
            content.bottom() <= body.bottom() - RecipeDrawerLayout.FOOTER);
    }
}
