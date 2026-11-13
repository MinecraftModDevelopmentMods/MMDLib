package zone.moddev.mc.lib.gui.model.elements;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.ElementAction;
import zone.moddev.mc.lib.gui.ElementActions;
import zone.moddev.mc.lib.gui.WidgetTypes;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Navigation;
import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.model.ValueContract;
import zone.moddev.mc.lib.gui.model.ViewRef;
import zone.moddev.mc.lib.gui.value.ValueType;
import zone.moddev.mc.lib.gui.value.ValueTypeRegistry;
import zone.moddev.mc.lib.gui.value.ValueTypes;

/**
 * {@code button}: a vanilla-style button. Its tooltip is required.
 *
 * <p>
 * {@code text}: the caption, if any {@code icon}: the icon sprite, if any
 * {@code shows}: the view it switches to, if it is a navigation button
 * {@code icons}: optional icons indexed by its boolean or whole-number value
 * {@code valueLabels}: optional translated names for those states in the
 * tooltip {@code flat}: whether to use a compact icon with a flat border and
 * background
 */
public class ButtonElement implements ButtonControl {
    private final Common common;
    private final Optional<Text> text;
    private final Optional<ResourceLocation> icon;
    private final Optional<ViewRef> shows;
    private final List<ResourceLocation> icons;
    private final List<Text> valueLabels;
    private final boolean flat;

    protected ButtonElement(ButtonElement source) {
        this(source.common, source.text, source.icon, source.shows,
            source.icons, source.valueLabels, source.flat);
    }

    public ButtonElement(Common common, Optional<Text> text,
        Optional<ResourceLocation> icon, Optional<ViewRef> shows,
        List<ResourceLocation> icons, List<Text> valueLabels, boolean flat) {
        icons = List.copyOf(icons);
        valueLabels = List.copyOf(valueLabels);

        this.common = common;
        this.text = text;
        this.icon = icon;
        this.shows = shows;
        this.icons = icons;
        this.valueLabels = valueLabels;
        this.flat = flat;
    }

    public ButtonElement(Common common, Optional<Text> text,
        Optional<ResourceLocation> icon, Optional<ViewRef> shows) {
        this(common, text, icon, shows, List.of(), List.of(), false);
    }

    public ButtonElement(Common common, Optional<Text> text,
        Optional<ResourceLocation> icon, Optional<ViewRef> shows,
        List<ResourceLocation> icons, List<Text> valueLabels) {
        this(common, text, icon, shows, icons, valueLabels, false);
    }

    private int stateIndex(Object value, int count) {
        long index = value instanceof Boolean b
            ? (b ? 1 : 0)
            : value instanceof Number n ? n.longValue() : 0;
        return index >= 0 && index < count ? (int) index : 0;
    }

    public Optional<ResourceLocation> iconFor(Object value) {
        return icons.isEmpty()
            ? icon
            : Optional.of(icons.get(stateIndex(value, icons.size())));
    }

    public Optional<Text> labelFor(Object value) {
        return valueLabels.isEmpty()
            ? Optional.empty()
            : Optional
                .of(valueLabels.get(stateIndex(value, valueLabels.size())));
    }

    public int[] measure(TextMeasure measure) {
        return flat
            ? new int[]{14, 14}
            : text.isPresent()
                ? new int[]{Math.max(20, measure.width(text.get()) + 8), 20}
                : new int[]{20, 20};
    }

    public Set<ValueType<?>> valueKinds(WidgetTypes widgets) {
        return icons.isEmpty() && valueLabels.isEmpty()
            ? ValueTypeRegistry.GLOBAL.bindingTypes()
            : Set.of(ValueTypes.BOOLEAN, ValueTypes.WHOLE);
    }

    public void validate() {
        ButtonControl.super.validate();
        if (flat && text.isPresent())
            throw new IllegalArgumentException(
                "A flat button uses an icon, not a text caption");
        if (common.tooltip().isEmpty())
            throw new IllegalArgumentException(
                "<button> needs a tooltip saying what it does");
        if (text.isEmpty() && icon.isEmpty() && icons.isEmpty())
            throw new IllegalArgumentException("<button> needs text or icon");
        if (!icons.isEmpty() || !valueLabels.isEmpty())
            ValueContract.require(this, "a stateful <button>", false);
        if (!icons.isEmpty() && !valueLabels.isEmpty()
            && icons.size() != valueLabels.size())
            throw new IllegalArgumentException(
                "button icons and value-labels must have equal counts");
        if (shows.isPresent() && common.clicks().any())
            throw new IllegalArgumentException(
                "A <button> that shows a view cannot also have click handlers");
    }

    public Optional<Navigation> navigation() {
        return shows.map(Navigation::new);
    }

    /**
     * Shared navigation action; custom controls declare {@link #navigation()}
     * too.
     */
    public static final ElementAction<ElementActions.Click> NAVIGATE = Navigation.NAVIGATE;

    public Common common() {
        return common;
    }

    public Optional<Text> text() {
        return text;
    }

    public Optional<ResourceLocation> icon() {
        return icon;
    }

    public Optional<ViewRef> shows() {
        return shows;
    }

    public List<ResourceLocation> icons() {
        return icons;
    }

    public List<Text> valueLabels() {
        return valueLabels;
    }

    public boolean flat() {
        return flat;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (object == null || getClass() != object.getClass())
            return false;
        var other = (ButtonElement) object;
        return java.util.Objects.equals(common, other.common)
            && java.util.Objects.equals(text, other.text)
            && java.util.Objects.equals(icon, other.icon)
            && java.util.Objects.equals(shows, other.shows)
            && java.util.Objects.equals(icons, other.icons)
            && java.util.Objects.equals(valueLabels, other.valueLabels)
            && java.util.Objects.equals(flat, other.flat);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(common);
        hash = 31 * hash + java.util.Objects.hashCode(text);
        hash = 31 * hash + java.util.Objects.hashCode(icon);
        hash = 31 * hash + java.util.Objects.hashCode(shows);
        hash = 31 * hash + java.util.Objects.hashCode(icons);
        hash = 31 * hash + java.util.Objects.hashCode(valueLabels);
        hash = 31 * hash + java.util.Objects.hashCode(flat);
        return hash;
    }

    @Override
    public String toString() {
        return "ButtonElement[common=" + common + ", text=" + text + ", icon="
            + icon + ", shows=" + shows + ", icons=" + icons + ", valueLabels="
            + valueLabels + ", flat=" + flat + "]";
    }
}
