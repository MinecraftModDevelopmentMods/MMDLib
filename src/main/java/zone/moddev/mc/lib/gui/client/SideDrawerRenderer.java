package zone.moddev.mc.lib.gui.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Element.Edge;

/** Shared background for a bookmark tab and its outward-opening drawer. */
public final class SideDrawerRenderer {
  private static final ResourceLocation BOOKMARK = ResourceLocation.parse("mmdlib:drawer_bookmark");
  private static final ResourceLocation PANEL = ResourceLocation.parse("mmdlib:panel");

  private SideDrawerRenderer() {}

  public static void tab(GuiGraphics graphics, Rect tab) {
    com.mojang.blaze3d.systems.RenderSystem.enableBlend();
    com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
    graphics.blitSprite(BOOKMARK, tab.x(), tab.y(), tab.width(), tab.height());
    com.mojang.blaze3d.systems.RenderSystem.disableBlend();
  }

  public static void open(GuiGraphics graphics, Rect tab, Rect body, Edge edge) {
    graphics.blitSprite(PANEL, body.x(), body.y(), body.width(), body.height());
    tab(graphics, tab);
    // Join the two bevels into one surface, preserving the bookmark's notched tail.
    int seam = edge == Edge.LEFT ? tab.x() : tab.right();
    graphics.fill(seam - 3, tab.y(), seam + 3, tab.y() + 1, 0xff000000);
    graphics.fill(seam - 3, tab.y() + 1, seam + 3, tab.y() + 3, 0xffffffff);
    graphics.fill(seam - 3, tab.y() + 3, seam + 3, tab.y() + 20, 0xffc6c6c6);
  }
}
