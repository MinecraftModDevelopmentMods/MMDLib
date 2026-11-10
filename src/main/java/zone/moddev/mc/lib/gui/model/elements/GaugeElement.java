package zone.moddev.mc.lib.gui.model.elements;

import static zone.moddev.mc.lib.gui.model.ValueContract.NUMBERS;

import java.util.Optional;
import java.util.Set;

import net.neoforged.neoforge.fluids.FluidStack;

import zone.moddev.mc.lib.gui.WidgetTypes;
import zone.moddev.mc.lib.gui.layout.ElementLayouts;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.GaugeFill;
import zone.moddev.mc.lib.gui.model.Orientation;
import zone.moddev.mc.lib.gui.model.ValueContract;
import zone.moddev.mc.lib.gui.value.ValueType;
import zone.moddev.mc.lib.gui.value.ValueTypes;

/**
 * {@code gauge}: the glass graduated gauge, 18 px thick. Filled with
 * {@code color} when given, otherwise with the fluid its value returns.
 *
 * <p>
 * {@code orientation}: which way it runs {@code length}: its length in GUI
 * pixels, or {@link Element#FILL} {@code color}: an optional ARGB solid fill
 */
public class GaugeElement implements Element {
    private final Common common;
    private final Orientation orientation;
    private final int length;
    private final Optional<Integer> color;

    public GaugeElement(Common common, Orientation orientation, int length,
        Optional<Integer> color) {
        this.common = common;
        this.orientation = orientation;
        this.length = length;
        this.color = color;
    }

    /** Whether the length is {@code fill}. */
    public boolean fills() {
        return length == FILL;
    }

    /**
     * Selects visual contents independently of display values and fill ratio;
     * subclasses may override.
     */
    public GaugeFill fill(Object value) {
        if (color().isPresent())
            return GaugeFill.solid(color().get());
        return value instanceof FluidStack fluid
            ? GaugeFill.fluid(fluid)
            : GaugeFill.solid(0x777777);
    }

    @Override
    public Object displayValue(Object value) {
        return value instanceof FluidStack fluid ? fluid.getAmount() : value;
    }

    public int[] measure(TextMeasure text) {
        return ElementLayouts.oriented(orientation, 18, fills() ? 8 : length);
    }

    public boolean stretchX() {
        return fills();
    }

    public boolean stretchY() {
        return fills();
    }

    public Set<ValueType<?>> valueKinds(WidgetTypes widgets) {
        return color.isPresent()
            ? NUMBERS
            : Set.of(ValueTypes.WHOLE, ValueTypes.DECIMAL, ValueTypes.NUMBER,
                ValueTypes.FLUID);
    }

    public void validate() {
        Element.super.validate();
        ValueContract.require(this, "a <gauge>", true);
    }

    public Optional<Orientation> fillOrientation() {
        return fills() ? Optional.of(orientation()) : Optional.empty();
    }

    public Common common() {
        return common;
    }

    public Orientation orientation() {
        return orientation;
    }

    public int length() {
        return length;
    }

    public Optional<Integer> color() {
        return color;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (object == null || getClass() != object.getClass())
            return false;
        var other = (GaugeElement) object;
        return java.util.Objects.equals(common, other.common)
            && java.util.Objects.equals(orientation, other.orientation)
            && java.util.Objects.equals(length, other.length)
            && java.util.Objects.equals(color, other.color);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(common);
        hash = 31 * hash + java.util.Objects.hashCode(orientation);
        hash = 31 * hash + java.util.Objects.hashCode(length);
        hash = 31 * hash + java.util.Objects.hashCode(color);
        return hash;
    }

    @Override
    public String toString() {
        return "GaugeElement[common=" + common + ", orientation=" + orientation
            + ", length=" + length + ", color=" + color + "]";
    }
}
