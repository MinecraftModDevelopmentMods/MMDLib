package zone.moddev.mc.lib.gui.client;

import zone.moddev.mc.lib.gui.model.Orientation;
import zone.moddev.mc.lib.gui.model.elements.MeterElement;

/**
 * Segmented meter drawing, independent of the machine providing its numeric
 * values.
 */
public final class MeterRenderer {
    private MeterRenderer() {
    }

    public static void draw(MeterElement m, ElementRenderers.Context c) {
        var g = c.graphics();
        var r = c.bounds();
        g.fill(r.x(), r.y(), r.right(), r.bottom(),
            m.gapColor().orElse(0xff000000));
        boolean vertical = m.orientation() == Orientation.VERTICAL;
        int length = (vertical ? r.height() : r.width()) - 2;
        int count = Math.max(1,
            Math.round((float) (length - m.gap()) / (m.segment() + m.gap())));
        int space = length - m.gap() - count * m.gap(), pos = 1 + m.gap();
        for (int i = 0; i < count; i++) {
            int size = space / count + (i < space % count ? 1 : 0);
            int color = i < Math.round(count * c.ratio())
                ? m.color()
                : 0xff000000 | ((m.color() & 0xfefefe) >> 1 & 0x7f7f7f);
            if (vertical)
                g.fill(r.x() + 1, r.bottom() - pos - size, r.right() - 1,
                    r.bottom() - pos, color);
            else
                g.fill(r.x() + pos, r.y() + 1, r.x() + pos + size,
                    r.bottom() - 1, color);
            pos += size + m.gap();
        }
        ElementDrawing.sprite(g, "compact_frame", r);
    }
}
