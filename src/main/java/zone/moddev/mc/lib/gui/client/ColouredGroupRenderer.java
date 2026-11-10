package zone.moddev.mc.lib.gui.client;

import net.minecraft.client.gui.GuiGraphics;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.containers.ColouredGroupElement;

/** Outline pass runs after child backgrounds. */
public final class ColouredGroupRenderer {
  private ColouredGroupRenderer() {}

  public static void draw(
      ColouredGroupElement group, GuiGraphics graphics, Rect bounds, boolean highlighted) {
    Rect r = group.outline(bounds, highlighted);
    graphics.renderOutline(r.x(), r.y(), r.width(), r.height(), group.colour());
  }
}
