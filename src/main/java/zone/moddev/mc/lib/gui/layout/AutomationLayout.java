package zone.moddev.mc.lib.gui.layout;

/** Shared geometry for automation rendering, real menu slots, hit boxes and exclusions. */
public final class AutomationLayout {
  public static final int WIDTH = 112, RAIL = ScreenLayout.POPOUT_TAB;
  public static final int TOP = ScreenLayout.TAB_PADDING, TAB_HEIGHT = ScreenLayout.POPOUT_HEIGHT;
  public static final int SELECTOR_WIDTH = 22, SELECTOR_PITCH = 16, SELECTOR_LIMIT = 8;
  public static final int EDITOR_X = SELECTOR_WIDTH + 2, EDITOR_Y = 132, PADDING = 4;
  public static final int EDITOR_HEADER = 18, FILTER_ITEM_SIZE = 12;
  public static final int SELECTOR_ROW_HEIGHT = 18;
  private static final int[][] FACES = {{1, 1}, {2, 2}, {0, 1}, {2, 1}, {1, 0}, {1, 2}};

  private AutomationLayout() {}

  public static Rect face(int side) {
    return new Rect(36 + FACES[side][0] * 18, 7 + FACES[side][1] * 18, 18, 18);
  }

  public static Rect addon(int column) {
    return new Rect(26 + column * 20, 74, 18, 18);
  }

  public static Rect filter(int column) {
    return new Rect(28 + column * 20, 96, 14, 14);
  }

  public static Rect filterToggle(int column) {
    var filter = filter(column);
    return new Rect(filter.x(), filter.bottom() + 2, filter.width(), 7);
  }

  /** Item bounds inside a frame, relative to the drawer body; addon slots precede filters. */
  public static Rect item(int subSlot) {
    var frame = subSlot < 4 ? addon(subSlot) : filter(subSlot - 4);
    int size = subSlot < 4 ? 16 : FILTER_ITEM_SIZE;
    return new Rect(frame.x() + 1, frame.y() + 1, size, size);
  }

  public static int contentHeight(boolean filterSlot, boolean configuredFilter) {
    return (configuredFilter
            ? filterToggle(0).bottom()
            : filterSlot ? filter(0).bottom() : addon(0).bottom())
        + PADDING;
  }
}
