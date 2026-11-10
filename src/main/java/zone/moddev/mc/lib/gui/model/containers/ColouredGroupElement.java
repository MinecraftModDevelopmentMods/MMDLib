package zone.moddev.mc.lib.gui.model.containers;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import zone.moddev.mc.lib.api.automation.ResourceType;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.ElementGroup;
import zone.moddev.mc.lib.gui.model.InventoryScope;
import zone.moddev.mc.lib.gui.model.Screen;

/**
 * A single-child decorator. Its outline does not add padding or change the
 * child's size.
 */
public class ColouredGroupElement implements Element {
    private final Common common;
    private final int colour;
    private final Element child;

    public ColouredGroupElement(Common common, int colour, Element child) {
        Objects.requireNonNull(common);
        Objects.requireNonNull(child);

        this.common = common;
        this.colour = colour;
        this.child = child;
    }

    public List<Element> children() {
        return List.of(child);
    }

    public int[] measure(TextMeasure text) {
        return child.measure(text);
    }

    public boolean stretchX() {
        return child.stretchX();
    }

    public boolean stretchY() {
        return child.stretchY();
    }

    public boolean stretchToPanel() {
        return child.stretchToPanel();
    }

    public Map<Element, Rect> childLayout(Rect bounds, TextMeasure text) {
        return Map.of(child, new Rect(0, 0, bounds.width(), bounds.height()));
    }

    @Override
    public List<String> associatedGroups(Screen screen,
        List<ElementGroup> groups) {
        var explicit = Element.super.associatedGroups(screen, groups);
        return groups.stream().filter(
            group -> explicit.contains(group.id()) || (group.anchors().isEmpty()
                && child instanceof InventoryScope scope
                && group.members().filter(
                    members -> members.type().equals(ResourceType.ITEMS.id())
                        && members.inventory().equals(scope.inventory()))
                    .isPresent()))
            .map(ElementGroup::id).toList();
    }

    @Override
    public boolean replacesChildHighlight(Screen screen, Element descendant,
        String group, List<ElementGroup> groups) {
        return child instanceof InventoryScope scope
            && InventoryScope.owner(screen, descendant).orElse(null) == scope;
    }

    public Rect outline(Rect bounds, boolean highlighted) {
        return highlighted
            ? new Rect(bounds.x() - 1, bounds.y() - 1, bounds.width() + 2,
                bounds.height() + 2)
            : bounds;
    }

    public boolean verticalChildren(boolean verticalParent) {
        return verticalParent;
    }

    public Common common() {
        return common;
    }

    public int colour() {
        return colour;
    }

    public Element child() {
        return child;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (object == null || getClass() != object.getClass())
            return false;
        var other = (ColouredGroupElement) object;
        return java.util.Objects.equals(common, other.common)
            && java.util.Objects.equals(colour, other.colour)
            && java.util.Objects.equals(child, other.child);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(common);
        hash = 31 * hash + java.util.Objects.hashCode(colour);
        hash = 31 * hash + java.util.Objects.hashCode(child);
        return hash;
    }

    @Override
    public String toString() {
        return "ColouredGroupElement[common=" + common + ", colour=" + colour
            + ", child=" + child + "]";
    }
}
