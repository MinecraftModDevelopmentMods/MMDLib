package zone.moddev.mc.lib.gui.client;

import net.minecraft.client.gui.GuiGraphics;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Element;

/** Pixel battery silhouette with charge bars; no tank glass, fluid texture or graduations. */
final class EnergyGaugeRenderer {
  // A narrow reflection rolls into a shaded edge, like Tesla Core Lib's RF meter artwork.
  private static final int[] BAR_LIGHT = {-30, 10, 48, 24, 8, 0, -4, -10, -18, -28, -40, -52};

  private EnergyGaugeRenderer() {}

  static void draw(GuiGraphics g, Rect r, Element.EnergyGauge gauge, double ratio) {
    boolean vertical = gauge.orientation() == Element.Orientation.VERTICAL;
    int length = vertical ? r.height() : r.width();
    int body = length - 3;
    // The terminal and chamfered casing fit entirely inside the element's bounds.
    fill(g, r, vertical, 6, body, 6, 3, 0xff50565f);
    fill(g, r, vertical, 7, body, 2, 3, 0xffd8dfe5);
    fill(g, r, vertical, 9, body, 2, 3, 0xff929da8);
    fill(g, r, vertical, 7, body + 2, 4, 1, 0xfff0f3f6);
    fill(g, r, vertical, 0, 1, 18, body - 2, 0xff242830);
    fill(g, r, vertical, 1, 0, 16, body, 0xff454c55);
    fill(g, r, vertical, 1, 1, 1, body - 2, 0xffd5dde5);
    fill(g, r, vertical, 2, body - 1, 14, 1, 0xffecf0f4);
    fill(g, r, vertical, 16, 1, 1, body - 2, 0xff707b88);
    fill(g, r, vertical, 2, 0, 14, 1, 0xff343b45);
    int inside = body - 4;
    fill(g, r, vertical, 2, 2, 14, inside, 0xff202226);
    fill(g, r, vertical, 3, 2, 2, inside, 0xff30353c);
    fill(g, r, vertical, 5, 2, 2, inside, 0xff282d34);
    fill(g, r, vertical, 14, 2, 2, inside, 0xff15181e);

    // Clip the last charged bar to the actual level, keeping empty and full exact.
    int charged = (int) Math.round(inside * ratio);
    int count = Math.max(1, (inside + 1) / 6);
    int available = inside - (count - 1);
    int position = 0;
    for (int i = 0; i < count; i++) {
      int size = available / count + (i < available % count ? 1 : 0);
      int lit = Math.max(0, Math.min(size, charged - position));
      if (lit > 0) {
        for (int x = 0; x < BAR_LIGHT.length; x++)
          fill(g, r, vertical, 3 + x, 2 + position, 1, lit, shade(gauge.color(), BAR_LIGHT[x]));
        if (lit >= 3) {
          fill(g, r, vertical, 3, 2 + position, 12, 1, shade(gauge.color(), -45));
          fill(g, r, vertical, 4, 1 + position + lit, 9, 1, shade(gauge.color(), 30));
        }
      }
      position += size + 1;
    }
  }

  /** Lighten towards white or shade towards black, retaining the configured color's alpha. */
  private static int shade(int color, int light) {
    int result = color & 0xff000000;
    int target = light > 0 ? 255 : 0;
    for (int shift = 0; shift <= 16; shift += 8) {
      int channel = (color >>> shift) & 255;
      channel += (target - channel) * Math.abs(light) / 100;
      result |= channel << shift;
    }
    return result;
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
