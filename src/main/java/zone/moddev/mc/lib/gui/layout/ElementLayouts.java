package zone.moddev.mc.lib.gui.layout;

import java.util.*;

import zone.moddev.mc.lib.gui.model.Align;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Orientation;

/** Reusable geometry operations. Element types choose which operations to use. */
public final class ElementLayouts {
  private ElementLayouts() {}

  public static int[] size(Element element, TextMeasure text) {
    int[] size = element.measure(text);
    if (size == null || size.length != 2 || size[0] < 0 || size[1] < 0)
      throw new IllegalArgumentException(
          "Invalid measured size for " + element.getClass().getName());
    return size;
  }

  public static int[] stack(List<? extends Element> children, int gap, TextMeasure text) {
    int w = 0, h = Math.max(0, children.size() - 1) * gap;
    for (var child : children) {
      var size = size(child, text);
      w = Math.max(w, size[0]);
      h += size[1];
    }
    return new int[] {w, h};
  }

  public static int[] row(List<? extends Element> children, int gap, TextMeasure text) {
    int w = Math.max(0, children.size() - 1) * gap, h = 0;
    for (var child : children) {
      var size = size(child, text);
      w += size[0];
      h = Math.max(h, size[1]);
    }
    return new int[] {w, h};
  }

  public static int[] oriented(Orientation orientation, int thickness, int length) {
    return orientation == Orientation.VERTICAL
        ? new int[] {thickness, length}
        : new int[] {length, thickness};
  }

  public static Map<Element, Rect> stackPositions(
      List<? extends Element> children, Rect bounds, int gap, TextMeasure text) {
    var result = new IdentityHashMap<Element, Rect>();
    int y = bounds.y();
    for (var child : children) {
      var size = size(child, text);
      result.put(
          child, new Rect(bounds.x(), y, child.stretchX() ? bounds.width() : size[0], size[1]));
      y += size[1] + gap;
    }
    return result;
  }

  public static int align(Align align, int room, int size) {
    return switch (align) {
      case START -> 0;
      case CENTER -> (room - size) / 2;
      case END -> room - size;
    };
  }
}
