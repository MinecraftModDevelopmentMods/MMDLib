package zone.moddev.mc.lib.gui.client;

import net.minecraft.client.gui.GuiGraphics;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Element;

/** Glossy charge bars inside the same slim casing as the fluid gauge, without graduations. */
final class EnergyGaugeRenderer {
  // A narrow reflection rolls into a shaded edge, like Tesla Core Lib's RF meter artwork.
  private static final int[] BAR_LIGHT = {-30, 10, 48, 24, 8, 0, -4, -10, -18, -28, -40, -52};

  private EnergyGaugeRenderer() {}

  static void draw(GuiGraphics g, Rect r, Element.EnergyGauge gauge, double ratio) {
    boolean vertical = gauge.orientation() == Element.Orientation.VERTICAL;
    int length = vertical ? r.height() : r.width();
    GaugeRenderer.background(g, r, vertical);
    int inside = length - 4;

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

  /** Coordinates run from the bottom upwards, or left to right for a horizontal gauge. */
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
