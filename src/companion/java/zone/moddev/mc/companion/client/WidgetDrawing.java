package zone.moddev.mc.companion.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import zone.moddev.mc.lib.gui.layout.Rect;

final class WidgetDrawing {
    private WidgetDrawing() {
    }

    static Component text(String name, Object... arguments) {
        return Component.translatable("screen.mmdlib_companion." + name,
            arguments);
    }

    static void button(GuiGraphics g, Rect r, String label, boolean selected,
        boolean enabled) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(),
            selected ? 0xff66bb88 : 0xff444444);
        g.fill(r.x() + 1, r.y() + 1, r.right() - 1, r.bottom() - 1,
            enabled ? 0xff727272 : 0xff505050);
        var font = Minecraft.getInstance().font;
        var text = text(label);
        g.drawString(font, text, r.x() + (r.width() - font.width(text)) / 2,
            r.y() + 5, enabled ? 0xffffffff : 0xffaaaaaa, false);
    }
}
