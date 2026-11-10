package zone.moddev.mc.lib.gui.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Edge;

/** Coloured edge tabs and full-height drawer backgrounds from the machine GUI reference. */
public final class SideDrawerRenderer {
  private static final ResourceLocation TAB = ResourceLocation.parse("mmdlib:drawer_tab");
  private static final ResourceLocation PANEL = ResourceLocation.parse("mmdlib:drawer_panel");

  private SideDrawerRenderer() {}

  private static void sprite(GuiGraphics graphics, ResourceLocation sprite, Rect rect, int color) {
    com.mojang.blaze3d.systems.RenderSystem.enableBlend();
    com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
    graphics.setColor(
        ((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f, 1);
    graphics.blitSprite(sprite, rect.x(), rect.y(), rect.width(), rect.height());
    graphics.setColor(1, 1, 1, 1);
    com.mojang.blaze3d.systems.RenderSystem.disableBlend();
  }

  public static void tab(GuiGraphics graphics, Rect tab, int color) {
    sprite(graphics, TAB, tab, color);
  }

  public static void open(GuiGraphics graphics, Rect tab, Rect body, Edge edge, int color) {
    int x = Math.min(tab.x(), body.x());
    var whole = new Rect(x, body.y(), body.width() + tab.width(), body.height());
    sprite(graphics, PANEL, whole, color);
    // The narrow, darker rail retains the active icon beside the main machine panel.
    int rail = 0xff000000 | (((color & 0xfefefe) >> 1) + ((color & 0xfcfcfc) >> 2));
    graphics.fill(tab.x() + 2, body.y() + 2, tab.right() - 2, body.bottom() - 2, rail);
    int seam = edge == Edge.LEFT ? tab.x() : tab.right() - 1;
    graphics.fill(
        seam, body.y() + 2, seam + 1, body.bottom() - 2, 0xff000000 | ((color & 0xfefefe) >> 1));
    tab(graphics, tab, color);
  }
}
