package zone.moddev.mc.lib.gui.layout;

/** Container page geometry from provider settings, within the shared inset drawer body. */
public record ContainerDrawerLayout(
    Rect tab,
    Rect body,
    int columns,
    int rows,
    zone.moddev.mc.lib.gui.runtime.ContainerPolicy.Layout policy) {
  public ContainerDrawerLayout(Rect tab, Rect body, int columns, int rows, boolean equipment) {
    this(tab, body, columns, rows, preset(equipment));
  }

  private static zone.moddev.mc.lib.gui.runtime.ContainerPolicy.Layout preset(boolean equipment) {
    return (equipment
            ? zone.moddev.mc.lib.gui.runtime.ContainerPolicy.EQUIPMENT
            : zone.moddev.mc.lib.gui.runtime.ContainerPolicy.STORAGE)
        .settings()
        .layout();
  }

  public ContainerDrawerLayout(Rect tab, Rect body, int columns, int rows) {
    this(tab, body, columns, rows, false);
  }

  /** Content geometry consumes the tab allocated by the drawer registry. */
  public static ContainerDrawerLayout of(
      Rect panel, int bottom, int slots, Rect tab, boolean equipment) {
    return of(
        panel,
        bottom,
        slots,
        tab,
        equipment,
        zone.moddev.mc.lib.gui.model.Edge.LEFT,
        Integer.MAX_VALUE);
  }

  public static ContainerDrawerLayout of(
      Rect panel,
      int bottom,
      int slots,
      Rect tab,
      boolean equipment,
      zone.moddev.mc.lib.gui.model.Edge edge,
      int screenWidth) {
    return of(panel, bottom, slots, tab, preset(equipment), edge, screenWidth);
  }

  public static ContainerDrawerLayout of(
      Rect panel,
      int bottom,
      int slots,
      Rect tab,
      zone.moddev.mc.lib.gui.runtime.ContainerPolicy.Layout policy,
      zone.moddev.mc.lib.gui.model.Edge edge,
      int screenWidth) {
    var body =
        SideDrawerLayout.body(
            panel,
            bottom,
            SideDrawerLayout.bodyX(edge, tab, policy.width()),
            SideDrawerLayout.top(panel),
            policy.width(),
            Integer.MAX_VALUE);
    int columns = policy.columns();
    int rows =
        Math.min(
            policy.maxRows(),
            Math.max(0, (body.height() - policy.cellY() - policy.footer()) / policy.strideY()));
    if (SideDrawerLayout.room(edge, tab, screenWidth) < policy.width() || rows == 0)
      return new ContainerDrawerLayout(tab, new Rect(0, 0, 0, 0), 0, 0, policy);
    rows = Math.min(rows, Math.max(1, (slots + columns - 1) / columns));
    return new ContainerDrawerLayout(tab, body, columns, rows, policy);
  }

  public int pageSize() {
    return columns * rows;
  }

  public Rect rail() {
    return SideDrawerLayout.rail(tab, body);
  }

  public Rect cell(int visible) {
    return policy.cell(visible).offset(body.x(), body.y());
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
