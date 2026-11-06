package zone.moddev.mc.lib.gui.layout;

/** Six-column left drawer filling the shared inset panel height, with a tab beside the hotbar. */
public record ContainerDrawerLayout(Rect tab, Rect body, int columns, int rows, boolean equipment) {
  private static final int BODY_WIDTH = 6 * 18 + 12;

  public ContainerDrawerLayout(Rect tab, Rect body, int columns, int rows) {
    this(tab, body, columns, rows, false);
  }

  public static ContainerDrawerLayout equipment(
      Rect panel, int bottom, int slots, boolean backpack) {
    var base = of(panel, bottom, slots);
    var tab = base.tab().offset(0, backpack ? -24 : 0);
    var body = base.body();
    int height = body.height();
    int rows = Math.min(27, Math.max(0, (height - 56) / 22));
    if (tab.x() - BODY_WIDTH < 4 || rows == 0)
      return new ContainerDrawerLayout(tab, new Rect(0, 0, 0, 0), 0, 0, true);
    rows = Math.min(rows, Math.max(1, (slots + 1) / 2));
    return new ContainerDrawerLayout(tab, body, 2, rows, true);
  }

  public static ContainerDrawerLayout of(Rect panel, int bottom, int slots) {
    var tab =
        new Rect(
            Math.max(0, panel.x() - 22),
            Math.max(SideDrawerLayout.top(panel), SideDrawerLayout.bottom(panel, bottom) - 24),
            22,
            24);
    int columns = 6;
    int width = BODY_WIDTH;
    var body =
        SideDrawerLayout.body(
            panel, bottom, tab.x() - width, SideDrawerLayout.top(panel), width, Integer.MAX_VALUE);
    int height = body.height();
    int rows = Math.min(9, Math.max(0, (height - 44) / 18));
    if (tab.x() - width < 4 || rows == 0)
      return new ContainerDrawerLayout(tab, new Rect(0, 0, 0, 0), 0, 0);
    rows = Math.min(rows, Math.max(1, (slots + columns - 1) / columns));
    return new ContainerDrawerLayout(tab, body, columns, rows);
  }

  public int pageSize() {
    return columns * rows;
  }

  public Rect rail() {
    return SideDrawerLayout.rail(tab, body);
  }

  public Rect cell(int visible) {
    if (equipment)
      return new Rect(
          body.right() - 44 + visible % 2 * 20, body.y() + 34 + visible / 2 * 22, 18, 18);
    return new Rect(
        body.x() + 6 + visible % columns * 18, body.y() + 22 + visible / columns * 18, 18, 18);
  }

  public Rect previous() {
    return new Rect(body.x() + 3, body.bottom() - 18, 10, 14);
  }

  public Rect next() {
    return new Rect(body.right() - 13, body.bottom() - 18, 10, 14);
  }

  public Rect selection() {
    return new Rect(body.x() + 4, body.y() + 4, body.width() - 8, 16);
  }
}
