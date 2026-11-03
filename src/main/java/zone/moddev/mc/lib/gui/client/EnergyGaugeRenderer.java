package zone.moddev.mc.lib.gui.client;

import net.minecraft.client.gui.GuiGraphics;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Element;

/** Pixel battery silhouette with charge bars; no tank glass, fluid texture or graduations. */
final class EnergyGaugeRenderer {
  private EnergyGaugeRenderer() {}

  static void draw(GuiGraphics g, Rect r, Element.EnergyGauge gauge, double ratio) {
    boolean vertical = gauge.orientation() == Element.Orientation.VERTICAL;
    int length = vertical ? r.height() : r.width();
    int body = length - 3;
    // The terminal and chamfered casing fit entirely inside the element's bounds.
    fill(g, r, vertical, 6, body, 6, 3, 0xff777b80);
    fill(g, r, vertical, 0, 1, 18, body - 2, 0xff383b40);
    fill(g, r, vertical, 1, 0, 16, body, 0xff383b40);
    fill(g, r, vertical, 1, 2, 1, body - 4, 0xff74787d);
    int inside = body - 4;
    fill(g, r, vertical, 2, 2, 14, inside, 0xff202226);

    // Clip the last charged bar to the actual level, keeping empty and full exact.
    int charged = (int) Math.round(inside * ratio);
    int count = Math.max(1, (inside + 1) / 6);
    int available = inside - (count - 1);
    int position = 0;
    for (int i = 0; i < count; i++) {
      int size = available / count + (i < available % count ? 1 : 0);
      int lit = Math.max(0, Math.min(size, charged - position));
      if (lit > 0) fill(g, r, vertical, 3, 2 + position, 12, lit, gauge.color());
      position += size + 1;
    }
  }

  /** Coordinates run from the battery's negative end towards its terminal. */
  private static void fill(
      GuiGraphics g, Rect r, boolean vertical, int cross, int along, int width, int length, int color) {
    if (width <= 0 || length <= 0) return;
    if (vertical)
      g.fill(r.x() + cross, r.bottom() - along - length,
          r.x() + cross + width, r.bottom() - along, color);
    else
      g.fill(r.x() + along, r.y() + cross,
          r.x() + along + length, r.y() + cross + width, color);
  }
}
