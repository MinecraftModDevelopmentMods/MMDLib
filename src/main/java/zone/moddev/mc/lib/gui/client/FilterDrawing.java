package zone.moddev.mc.lib.gui.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;

import zone.moddev.mc.lib.gui.layout.Rect;

final class FilterDrawing {
    private FilterDrawing() {
    }

    static CompoundTag configuration(Object value) {
        return value instanceof CompoundTag tag ? tag : new CompoundTag();
    }

    static void arrow(GuiGraphics g, Rect r, boolean forward) {
        for (int i = 0; i < 3; i++) {
            int d = forward ? i : 2 - i;
            g.fill(r.x() + d, r.y() + i, r.x() + d + 1, r.bottom() - i, 0xffeeeeee);
        }
    }

    static void button(GuiGraphics g, Rect r) {
        ElementDrawing.sprite(g, "slot", r);
    }
}
