package zone.moddev.mc.lib.gui.model.containers;

import java.util.List;
import java.util.Map;

import zone.moddev.mc.lib.gui.layout.*;
import zone.moddev.mc.lib.gui.model.Align;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;

/**
 * {@code vbox} (vertical) or {@code hbox} (horizontal): stacks its children.
 *
 * <p>{@code vertical}: true for {@code vbox} {@code padding}: space inside the box's edges, in GUI
 * pixels {@code spacing}: space between children {@code align}: cross-axis alignment of the
 * children
 */
public class BoxElement implements Element {
  private final Common common;
  private final boolean vertical;
  private final int padding;
  private final int spacing;
  private final Align align;
  private final List<Element> children;

  /** Copies the children. */
  public BoxElement(
      Common common,
      boolean vertical,
      int padding,
      int spacing,
      Align align,
      List<Element> children) {
    children = List.copyOf(children);

    this.common = common;
    this.vertical = vertical;
    this.padding = padding;
    this.spacing = spacing;
    this.align = align;
    this.children = children;
  }

  public int[] measure(TextMeasure text) {
    var inner =
        vertical
            ? ElementLayouts.stack(children, spacing, text)
            : ElementLayouts.row(children, spacing, text);
    return new int[] {inner[0] + 2 * padding, inner[1] + 2 * padding};
  }

  public boolean stretchX() {
    return !vertical;
  }

  public boolean stretchY() {
    return vertical;
  }

  public boolean stretchToPanel() {
    return true;
  }

  public Map<Element, Rect> childLayout(Rect bounds, TextMeasure text) {
    var result = new java.util.IdentityHashMap<Element, Rect>();
    int innerW = bounds.width() - 2 * padding,
        innerH = bounds.height() - 2 * padding,
        used = spacing * Math.max(0, children.size() - 1),
        spacers = 0;
    var sizes = new java.util.ArrayList<int[]>();
    for (var child : children) {
      var size = ElementLayouts.size(child, text);
      sizes.add(size);
      used += vertical ? size[1] : size[0];
      if (child.spacer()) spacers++;
    }
    int leftover = Math.max(0, (vertical ? innerH : innerW) - used),
        position = padding,
        spacerIndex = 0;
    for (int i = 0; i < children.size(); i++) {
      var child = children.get(i);
      var size = sizes.get(i);
      int main = vertical ? size[1] : size[0];
      if (child.spacer()) main = leftover / spacers + (spacerIndex++ < leftover % spacers ? 1 : 0);
      if (vertical) {
        int w = child.stretchX() ? innerW : size[0];
        result.put(
            child, new Rect(padding + ElementLayouts.align(align, innerW, w), position, w, main));
      } else {
        int h = child.stretchY() ? innerH : size[1];
        result.put(
            child, new Rect(position, padding + ElementLayouts.align(align, innerH, h), main, h));
      }
      position += main + spacing;
    }
    return result;
  }

  public boolean verticalChildren(boolean verticalParent) {
    return vertical;
  }

  public boolean acceptsSpacers() {
    return true;
  }

  public Common common() {
    return common;
  }

  public boolean vertical() {
    return vertical;
  }

  public int padding() {
    return padding;
  }

  public int spacing() {
    return spacing;
  }

  public Align align() {
    return align;
  }

  public List<Element> children() {
    return children;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) return true;
    if (object == null || getClass() != object.getClass()) return false;
    var other = (BoxElement) object;
    return java.util.Objects.equals(common, other.common)
        && java.util.Objects.equals(vertical, other.vertical)
        && java.util.Objects.equals(padding, other.padding)
        && java.util.Objects.equals(spacing, other.spacing)
        && java.util.Objects.equals(align, other.align)
        && java.util.Objects.equals(children, other.children);
  }

  @Override
  public int hashCode() {
    int hash = 0;
    hash = 31 * hash + java.util.Objects.hashCode(common);
    hash = 31 * hash + java.util.Objects.hashCode(vertical);
    hash = 31 * hash + java.util.Objects.hashCode(padding);
    hash = 31 * hash + java.util.Objects.hashCode(spacing);
    hash = 31 * hash + java.util.Objects.hashCode(align);
    hash = 31 * hash + java.util.Objects.hashCode(children);
    return hash;
  }

  @Override
  public String toString() {
    return "BoxElement[common="
        + common
        + ", vertical="
        + vertical
        + ", padding="
        + padding
        + ", spacing="
        + spacing
        + ", align="
        + align
        + ", children="
        + children
        + "]";
  }
}
