package zone.moddev.mc.lib.gui.client;

import java.util.Objects;

import net.minecraft.client.gui.GuiGraphics;

import zone.moddev.mc.lib.gui.layout.Rect;

/** Presentation intent and fallback outline style, independent of the requesting UI feature. */
public record ElementHighlight(Kind kind, int colour, int outset) {
  public enum Kind {
    /** Persistent identification, for example a resource group's colour. */
    DECORATION,
    /** Emphasize the element's existing decoration, for example while hovering a group selector. */
    EMPHASIS,
    /** Mark a selected target with the requesting control's selection style. */
    SELECTION
  }

  public ElementHighlight {
    Objects.requireNonNull(kind);
    if (outset < 0) throw new IllegalArgumentException("Highlight outset must not be negative");
  }

  public static ElementHighlight decoration(int colour) {
    return new ElementHighlight(Kind.DECORATION, colour, 0);
  }

  public static ElementHighlight emphasis(int colour) {
    return new ElementHighlight(Kind.EMPHASIS, colour, 1);
  }

  public static ElementHighlight selection(int colour) {
    return new ElementHighlight(Kind.SELECTION, colour, 0);
  }

  public Rect outline(Rect bounds) {
    return new Rect(
        bounds.x() - outset,
        bounds.y() - outset,
        bounds.width() + 2 * outset,
        bounds.height() + 2 * outset);
  }

  /** Standard rectangular fallback; custom presentations may reuse it or draw another shape. */
  public void render(GuiGraphics graphics, Rect bounds) {
    var r = outline(bounds);
    graphics.renderOutline(r.x(), r.y(), r.width(), r.height(), colour);
  }
}
