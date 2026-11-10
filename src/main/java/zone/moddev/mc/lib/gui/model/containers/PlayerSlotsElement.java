package zone.moddev.mc.lib.gui.model.containers;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.load.ScreenValidationContext;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Screen;
import zone.moddev.mc.lib.gui.model.elements.PlayerSlotElement;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;

/** Immutable player-inventory section; its ordinary slot children own native interactions. */
public abstract class PlayerSlotsElement implements Element {
    private final Common common;
    private final List<Element> slots;

    protected PlayerSlotsElement(Common common, List<Integer> indices) {
        this(common, indices, PlayerSlotElement::player);
    }

    /** Factory is evaluated once; no overridable method runs during base construction. */
    protected PlayerSlotsElement(
        Common common,
        List<Integer> indices,
        java.util.function.IntFunction<? extends SlotElement> factory) {
        this(
            common,
            indices.stream()
                .map(
                    index -> {
                        var slot = Objects.requireNonNull(factory.apply(index));
                        if (!slot.playerInventory() || slot.index() != index)
                            throw new IllegalArgumentException(
                                "Player slot factory must preserve backing and requested index "
                                    + index);
                        return slot;
                    })
                .toList());
    }

    /** Supply stable ordinary slot children, whose configuration participates in model identity. */
    protected PlayerSlotsElement(Common common, Collection<? extends SlotElement> slots) {
        this.common = Objects.requireNonNull(common);
        if (slots.stream().anyMatch(slot -> !slot.playerInventory()))
            throw new IllegalArgumentException("Player sections require player-backed slots");
        this.slots = List.copyOf(slots);
    }

    public Common common() {
        return common;
    }

    public List<Element> children() {
        return slots;
    }

    public abstract String part();

    protected abstract Rect position(int index);

    /** Placement relative to the standard inventory footer when omitted from XML. */
    public abstract Rect defaultBounds(Rect inventory);

    public Optional<Element> visibilityAnchor(Screen screen) {
        if (screen.implicitElements().stream().noneMatch(element -> element == this))
            return Optional.empty();
        return screen.elements().stream()
            .filter(PlayerInventoryElement.class::isInstance)
            .filter(element -> element != this)
            .findFirst();
    }

    public Map<Element, Rect> childLayout(Rect bounds, TextMeasure text) {
        var result = new IdentityHashMap<Element, Rect>();
        for (var child : children())
            result.put(child, position(((SlotElement) child).index()));
        return result;
    }

    public void validateScreen(ScreenValidationContext context) {
        Element.super.validateScreen(context);
        context.unique(
            net.minecraft.resources.ResourceLocation.parse("mmdlib:player_inventory"),
            part(),
            "Player " + part() + " may appear only once");
        if (!context.screen().playerInventory())
            throw new IllegalArgumentException(
                "Player inventory sections cannot be placed with inventory=\"none\"");
    }

    @Override
    public boolean equals(Object other) {
        return this == other
            || other != null
                && getClass() == other.getClass()
                && common.equals(((PlayerSlotsElement) other).common)
                && slots.equals(((PlayerSlotsElement) other).slots);
    }

    @Override
    public int hashCode() {
        return Objects.hash(common, slots);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[common=" + common + ", children=" + slots + "]";
    }
}
