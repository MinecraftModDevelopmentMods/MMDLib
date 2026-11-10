package zone.moddev.mc.lib.gui.client;

import net.minecraft.client.gui.GuiGraphics;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Orientation;
import zone.moddev.mc.lib.gui.model.elements.EnergyGaugeElement;

/** Thin, lightly glossy charge bars inside the shared slim casing, without graduations. */
final class EnergyGaugeRenderer {
    // A soft reflection and restrained edge shading keep the narrow bars legible.
    private static final int[] BAR_LIGHT = {4, 12, 18, 12, 5, 0, 0, -2, -4, -6, -10, -16};

    private EnergyGaugeRenderer() {
    }

    static void draw(GuiGraphics g, Rect r, EnergyGaugeElement gauge, double ratio) {
        boolean vertical = gauge.orientation() == Orientation.VERTICAL;
        int length = vertical ? r.height() : r.width();
        GaugeRenderer.background(g, r, vertical);
        int inside = length - 4;

        // Clip the last charged bar to the actual level, keeping empty and full exact.
        int charged = (int) Math.round(inside * ratio);
        // At most four pixels per bar, with a one-pixel gap between neighbours.
        int count = Math.max(1, (inside + 5) / 5);
        int available = inside - (count - 1);
        int position = 0;
        for (int i = 0; i < count; i++) {
            int size = available / count + (i < available % count ? 1 : 0);
            int lit = Math.max(0, Math.min(size, charged - position));
            if (lit > 0) {
                for (int x = 0; x < BAR_LIGHT.length; x++)
                    fill(g, r, vertical, 3 + x, 2 + position, 1, lit,
                        shade(gauge.color(), BAR_LIGHT[x]));
                if (lit >= 3) {
                    fill(g, r, vertical, 3, 2 + position, 12, 1, shade(gauge.color(), -22));
                }
                if (lit >= 2)
                    fill(g, r, vertical, 4, 1 + position + lit, 10, 1, shade(gauge.color(), 28));
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
        GuiGraphics g,
        Rect r,
        boolean vertical,
        int cross,
        int along,
        int width,
        int length,
        int color) {
        if (width <= 0 || length <= 0)
            return;
        if (vertical)
            g.fill(
                r.x() + cross,
                r.bottom() - along - length,
                r.x() + cross + width,
                r.bottom() - along,
                color);
        else
            g.fill(r.x() + along, r.y() + cross, r.x() + along + length, r.y() + cross + width,
                color);
    }
}
