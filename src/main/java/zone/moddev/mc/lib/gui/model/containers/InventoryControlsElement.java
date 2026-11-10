package zone.moddev.mc.lib.gui.model.containers;

import java.util.List;

import zone.moddev.mc.lib.gui.layout.ElementLayouts;
import zone.moddev.mc.lib.gui.layout.LayoutContribution;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;

/**
 * Right-aligned buttons sharing the automatic inventory-colour row above player
 * inventory.
 */
public class InventoryControlsElement implements Element {
    private final Common common;
    private final List<Element> children;

    public InventoryControlsElement(Common common, List<Element> children) {
        children = List.copyOf(children);

        this.common = common;
        this.children = children;
    }

    public java.util.Optional<LayoutContribution> layoutContribution(
        LayoutContribution.Context context) {
        if (context.screen().elements().stream()
            .noneMatch(PlayerInventoryElement.class::isInstance))
            throw new IllegalArgumentException(
                "inventory-controls requires a player inventory");
        return java.util.Optional.empty();
    }

    public int[] measure(TextMeasure text) {
        return ElementLayouts.row(children, 2, text);
    }

    public java.util.Map<Element, Rect> childLayout(Rect bounds,
        TextMeasure text) {
        var result = new java.util.IdentityHashMap<Element, Rect>();
        int x = 0;
        for (var child : children) {
            var size = ElementLayouts.size(child, text);
            result.put(child,
                new Rect(x, (bounds.height() - size[1]) / 2, size[0], size[1]));
            x += size[0] + 2;
        }
        return result;
    }

    public Common common() {
        return common;
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
        var other = (InventoryControlsElement) object;
        return java.util.Objects.equals(common, other.common)
            && java.util.Objects.equals(children, other.children);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(common);
        hash = 31 * hash + java.util.Objects.hashCode(children);
        return hash;
    }

    @Override
    public String toString() {
        return "InventoryControlsElement[common=" + common + ", children="
            + children + "]";
    }
}
