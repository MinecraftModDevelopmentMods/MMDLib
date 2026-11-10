package zone.moddev.mc.lib.gui.model.containers;

import java.util.List;
import java.util.Optional;

import zone.moddev.mc.lib.gui.layout.ElementLayouts;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Text;

/**
 * {@code group}: a recessed panel around its children.
 *
 * <p>
 * {@code label}: an optional caption {@code tint}: an optional ARGB tint for the panel {@code
 * padding}: space inside the panel's edges
 */
public class GroupElement implements Element {
    private final Common common;
    private final Optional<Text> label;
    private final Optional<Integer> tint;
    private final int padding;
    private final List<Element> children;

    /** Copies the children. */
    public GroupElement(
        Common common,
        Optional<Text> label,
        Optional<Integer> tint,
        int padding,
        List<Element> children) {
        children = List.copyOf(children);

        this.common = common;
        this.label = label;
        this.tint = tint;
        this.padding = padding;
        this.children = children;
    }

    public int[] measure(TextMeasure text) {
        var inner = ElementLayouts.stack(children, 2, text);
        int header = label.isPresent() ? TextMeasure.LINE_HEIGHT + 2 : 0;
        return new int[]{
            Math.max(inner[0], label.map(text::width).orElse(0)) + 2 * padding,
            inner[1] + header + 2 * padding
        };
    }

    public java.util.Map<Element, Rect> childLayout(Rect bounds, TextMeasure text) {
        int header = label.isPresent() ? TextMeasure.LINE_HEIGHT + 2 : 0;
        return ElementLayouts.stackPositions(
            children,
            new Rect(
                padding,
                padding + header,
                bounds.width() - 2 * padding,
                bounds.height() - 2 * padding - header),
            2,
            text);
    }

    public Common common() {
        return common;
    }

    public Optional<Text> label() {
        return label;
    }

    public Optional<Integer> tint() {
        return tint;
    }

    public int padding() {
        return padding;
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
        var other = (GroupElement) object;
        return java.util.Objects.equals(common, other.common)
            && java.util.Objects.equals(label, other.label)
            && java.util.Objects.equals(tint, other.tint)
            && java.util.Objects.equals(padding, other.padding)
            && java.util.Objects.equals(children, other.children);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(common);
        hash = 31 * hash + java.util.Objects.hashCode(label);
        hash = 31 * hash + java.util.Objects.hashCode(tint);
        hash = 31 * hash + java.util.Objects.hashCode(padding);
        hash = 31 * hash + java.util.Objects.hashCode(children);
        return hash;
    }

    @Override
    public String toString() {
        return "GroupElement[common="
            + common
            + ", label="
            + label
            + ", tint="
            + tint
            + ", padding="
            + padding
            + ", children="
            + children
            + "]";
    }
}
