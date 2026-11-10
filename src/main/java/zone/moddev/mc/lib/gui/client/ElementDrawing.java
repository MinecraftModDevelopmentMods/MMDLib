package zone.moddev.mc.lib.gui.client;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Text;

/** Shared drawing primitives; element-specific decisions belong to their renderers. */
public final class ElementDrawing {
    private ElementDrawing() {
    }

    public static void blit(
        GuiGraphics g, ResourceLocation icon, int x, int y, int width, int height) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blitSprite(icon, x, y, width, height);
        RenderSystem.disableBlend();
    }

    public static void sprite(GuiGraphics g, String id, Rect r) {
        blit(
            g,
            ResourceLocation.parse(id.contains(":") ? id : "mmdlib:" + id),
            r.x(),
            r.y(),
            r.width(),
            r.height());
    }

    public static void tinted(GuiGraphics g, String sprite, Rect r, int color) {
        g.setColor(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f,
            1);
        sprite(g, sprite, r);
        g.setColor(1, 1, 1, 1);
    }

    public static void caption(ElementRenderers.Context c, Text text, int x, int y) {
        caption(c, c.access().text(text), x, y);
    }

    public static void caption(ElementRenderers.Context c, Component text, int x, int y) {
        c.graphics().drawString(c.access().font(), text, x, y, 0xff404040, false);
    }
}
