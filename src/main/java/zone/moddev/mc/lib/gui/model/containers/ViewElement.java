package zone.moddev.mc.lib.gui.model.containers;

import java.util.List;
import java.util.Optional;

import zone.moddev.mc.lib.gui.layout.ElementLayouts;
import zone.moddev.mc.lib.gui.layout.LayoutContext;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Text;

/**
 * {@code view}: one alternative content of a context area.
 *
 * <p>
 * {@code title}: an optional title drawn at its top
 */
public class ViewElement implements Element {
    private final Common common;
    private final Optional<Text> title;
    private final List<Element> children;

    /** Copies the children. */
    public ViewElement(Common common, Optional<Text> title, List<Element> children) {
        children = List.copyOf(children);

        this.common = common;
        this.title = title;
        this.children = children;
    }

    private int header(TextMeasure text) {
        return title.isPresent() || text instanceof LayoutContext context && context.hasHeader(this)
            ? TextMeasure.LINE_HEIGHT + 4
            : 0;
    }

    public int[] measure(TextMeasure text) {
        var inner = ElementLayouts.stack(children, 4, text);
        return new int[]{
            Math.max(inner[0], title.map(text::width).orElse(0) + 12), inner[1] + header(text)
        };
    }

    public java.util.Map<Element, Rect> childLayout(Rect bounds, TextMeasure text) {
        int top = header(text);
        return ElementLayouts.stackPositions(
            children, new Rect(0, top, bounds.width(), bounds.height() - top), 4, text);
    }

    public Common common() {
        return common;
    }

    public Optional<Text> title() {
        return title;
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
        var other = (ViewElement) object;
        return java.util.Objects.equals(common, other.common)
            && java.util.Objects.equals(title, other.title)
            && java.util.Objects.equals(children, other.children);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(common);
        hash = 31 * hash + java.util.Objects.hashCode(title);
        hash = 31 * hash + java.util.Objects.hashCode(children);
        return hash;
    }

    @Override
    public String toString() {
        return "ViewElement[common=" + common + ", title=" + title + ", children=" + children + "]";
    }
}
