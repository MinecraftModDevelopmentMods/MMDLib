package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import zone.moddev.mc.lib.gui.layout.ContainerDrawerLayout;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.SideDrawerLayout;

class ContainerDrawerLayoutTest {
  @Test
  void equipmentPairsStayTogetherAndUseASeparateTab() {
    var panel = new Rect(220, 30, 200, 274);
    for (int slots : new int[] {2, 6, 34, 4096}) {
      var layout = ContainerDrawerLayout.equipment(panel, 400, slots, true);
      var backpack = ContainerDrawerLayout.of(panel, 400, 27);
      assertEquals(backpack.body().width(), layout.body().width());
      assertEquals(backpack.body().x(), layout.body().x());
      assertEquals(panel.y() + SideDrawerLayout.MARGIN, layout.body().y());
      assertEquals(panel.height() - 2 * SideDrawerLayout.MARGIN, layout.body().height());
      // A tall text or automation drawer uses exactly the same vertical bounds.
      var content =
          SideDrawerLayout.body(
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
    var alone = ContainerDrawerLayout.equipment(panel, 400, 2, false);
    assertEquals(panel.bottom() - SideDrawerLayout.MARGIN, alone.tab().bottom());
    assertEquals(
        0, ContainerDrawerLayout.equipment(new Rect(120, 30, 200, 274), 400, 10, false).pageSize());
  }

  @Test
  void storageStaysOnScreenAndLeftOfTheStationaryPanel() {
    for (int left : new int[] {150, 220, 350}) {
      for (int bottom : new int[] {100, 180, 300}) {
        for (int slots : new int[] {1, 27, 54, 120, 4096}) {
          var panel = new Rect(left, 20, 200, 280);
          var layout = ContainerDrawerLayout.of(panel, bottom, slots);
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
              Math.min(panel.bottom() - SideDrawerLayout.MARGIN, bottom), layout.body().bottom());
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
    for (var panel :
        new Rect[] {
          new Rect(30, 10, 200, 180), new Rect(120, 10, 200, 180), new Rect(200, 4, 200, 40)
        }) {
      var layout = ContainerDrawerLayout.of(panel, panel.bottom(), 100);
      assertEquals(0, layout.pageSize());
      assertEquals(0, layout.body().width());
      assertTrue(layout.tab().x() >= 0);
    }
  }
}
