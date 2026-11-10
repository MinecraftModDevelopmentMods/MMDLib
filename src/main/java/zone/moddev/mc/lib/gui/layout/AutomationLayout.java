package zone.moddev.mc.lib.gui.layout;

import static zone.moddev.mc.lib.api.automation.AutomationInventory.COLUMNS;

import zone.moddev.mc.lib.gui.drawer.DrawerPlacement;
import zone.moddev.mc.lib.gui.model.containers.AutomationInventoryElement;

/** Shared geometry for automation rendering, real menu slots, hit boxes and exclusions. */
public final class AutomationLayout {
  private static final AutomationInventoryElement SLOTS = new AutomationInventoryElement("addons");
  public static final int WIDTH = 112, RAIL = DrawerPlacement.DEFAULT_WIDTH;
  public static final int TOP = SideDrawerLayout.MARGIN,
      TAB_HEIGHT = DrawerPlacement.DEFAULT_HEIGHT;
  public static final int SELECTOR_WIDTH = 22, SELECTOR_PITCH = 16, SELECTOR_LIMIT = 8;
  public static final int EDITOR_X = SELECTOR_WIDTH + 2, EDITOR_Y = 123, PADDING = 4;
  public static final int EDITOR_PADDING = 4, FILTER_ITEM_SIZE = 12;
  public static final int SELECTOR_ROW_HEIGHT = 18;
  private static final int[][] FACES = {{1, 1}, {2, 2}, {0, 1}, {2, 1}, {1, 0}, {1, 2}};

  private AutomationLayout() {}

  public static Rect face(int side) {
    return new Rect(36 + FACES[side][0] * 18, 7 + FACES[side][1] * 18, 18, 18);
  }

  public static Rect addon(int column) {
    return SLOTS.drawerSlotBounds(column);
  }

  public static Rect filter(int column) {
    return SLOTS.drawerSlotBounds(COLUMNS + column);
  }

  public static Rect filterToggle(int column) {
    return SLOTS.filterToggleBounds(column);
  }

  /** Item bounds inside a frame, relative to the drawer body; addon slots precede filters. */
  public static Rect item(int subSlot) {
    var element = SLOTS.slots().get(subSlot);
    var position = element.itemPosition(SLOTS.drawerSlotBounds(subSlot));
    int size = element.appearance().itemSize();
    return new Rect(position[0], position[1], size, size);
  }

  public static int contentHeight(boolean filterSlot, boolean configuredFilter) {
    return SLOTS.contentHeight(filterSlot, configuredFilter);
  }
}
