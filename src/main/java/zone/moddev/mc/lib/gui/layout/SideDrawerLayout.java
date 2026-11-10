package zone.moddev.mc.lib.gui.layout;

/** Shared panel insets and bounds for drawers with independent content and tab placement. */
public final class SideDrawerLayout {
  public static final int MARGIN = 4;

  /** Content drawers can grow to the viewport; inventory drawers stay aligned with the panel. */
  public enum HeightLimit {
    PANEL,
    SCREEN
  }

  private SideDrawerLayout() {}

  public static int bodyX(zone.moddev.mc.lib.gui.model.Edge edge, Rect tab, int width) {
    return edge == zone.moddev.mc.lib.gui.model.Edge.LEFT ? tab.x() - width : tab.right();
  }

  public static int room(zone.moddev.mc.lib.gui.model.Edge edge, Rect tab, int screenWidth) {
    return Math.max(
        0,
        edge == zone.moddev.mc.lib.gui.model.Edge.LEFT
            ? tab.x() - MARGIN
            : screenWidth - tab.right() - MARGIN);
  }

  public static int top(Rect panel) {
    return Math.max(MARGIN, panel.y() + MARGIN);
  }

  /** The screen/overlay limit already includes its own margin. */
  public static int bottom(Rect panel, int screenBottom) {
    return Math.min(panel.bottom() - MARGIN, screenBottom);
  }

  /** Fits a content-sized or full-height body between the shared panel and screen limits. */
  public static Rect body(
      Rect panel, int screenBottom, int x, int y, int width, int desiredHeight) {
    return body(panel, screenBottom, x, y, width, desiredHeight, HeightLimit.PANEL);
  }

  public static Rect body(
      Rect panel, int screenBottom, int x, int y, int width, int desiredHeight, HeightLimit limit) {
    int top = Math.max(top(panel), y);
    int bottom = limit == HeightLimit.SCREEN ? screenBottom : bottom(panel, screenBottom);
    int available = Math.max(0, bottom - top);
    return new Rect(x, top, width, Math.min(Math.max(0, desiredHeight), available));
  }

  public static Rect rail(Rect tab, Rect body) {
    return new Rect(tab.x(), body.y(), tab.width(), body.height());
  }
}
