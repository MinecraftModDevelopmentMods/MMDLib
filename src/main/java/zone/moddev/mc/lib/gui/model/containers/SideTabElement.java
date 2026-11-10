package zone.moddev.mc.lib.gui.model.containers;

import java.util.List;

import zone.moddev.mc.lib.gui.layout.ElementLayouts;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.SideDrawerLayout;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Edge;
import zone.moddev.mc.lib.gui.model.Element;

/**
 * {@code side-tab}: a tab growing out of the panel's edge, such as the upgrade
 * column.
 *
 * <p>
 * {@code edge}: the panel edge
 */
public class SideTabElement implements Element {
    private final Common common;
    private final Edge edge;
    private final List<Element> children;

    /** Copies the children. */
    public SideTabElement(Common common, Edge edge, List<Element> children) {
        children = List.copyOf(children);

        this.common = common;
        this.edge = edge;
        this.children = children;
    }

    public int[] measure(TextMeasure text) {
        var inner = ElementLayouts.stack(children, 2, text);
        return new int[]{inner[0] + 2 * SideDrawerLayout.MARGIN + 7,
            inner[1] + 2 * SideDrawerLayout.MARGIN};
    }

    public java.util.Map<Element, Rect> childLayout(Rect bounds,
        TextMeasure text) {
        int x = SideDrawerLayout.MARGIN
            + (edge == zone.moddev.mc.lib.gui.model.Edge.RIGHT ? 4 : 3);
        return ElementLayouts.stackPositions(children,
            new Rect(x, SideDrawerLayout.MARGIN,
                bounds.width() - 2 * SideDrawerLayout.MARGIN - 7,
                bounds.height() - 2 * SideDrawerLayout.MARGIN),
            2, text);
    }

    public Common common() {
        return common;
    }

    public Edge edge() {
        return edge;
    }

    public List<Element> children() {
        return children;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (object == null || getClass() != object.getClass())
            return false;
        var other = (SideTabElement) object;
        return java.util.Objects.equals(common, other.common)
            && java.util.Objects.equals(edge, other.edge)
            && java.util.Objects.equals(children, other.children);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(common);
        hash = 31 * hash + java.util.Objects.hashCode(edge);
        hash = 31 * hash + java.util.Objects.hashCode(children);
        return hash;
    }

    @Override
    public String toString() {
        return "SideTabElement[common=" + common + ", edge=" + edge
            + ", children=" + children + "]";
    }
}
