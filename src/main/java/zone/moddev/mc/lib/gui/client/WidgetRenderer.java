package zone.moddev.mc.lib.gui.client;

import net.minecraft.client.gui.GuiGraphics;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Element;

/**
 * Client-only drawing for a registered custom widget. Rendering is clipped to the widget bounds.
 * Balance any pose/scissor pushes of your own and do not retain or mutate the synced values. Clicks
 * and tooltips are handled by the screen using the widget's common XML attributes. For per-screen
 * editing state and input, register a {@link WidgetFactory} returning a {@link WidgetInstance}.
 */
@FunctionalInterface
public interface WidgetRenderer {
  void render(Context context);

  /**
   * One frame's inputs. Bounds and mouse coordinates are absolute GUI pixels. Values are synced
   * snapshots (whole numbers are BigInteger, decimals Double), or null before sync or on failure.
   * No getter is called on the client. partialTick supports client-only animation.
   */
  record Context(
      GuiGraphics graphics,
      Element.Custom widget,
      Rect bounds,
      Object value,
      Object maximum,
      int mouseX,
      int mouseY,
      float partialTick) {
    /** A clamped 0..1 numeric ratio, including large integers; unavailable values give zero. */
    public double ratio() {
      return ScreenText.ratio(value, maximum);
    }

    public boolean hovered() {
      return bounds.contains(mouseX, mouseY);
    }
  }
}
