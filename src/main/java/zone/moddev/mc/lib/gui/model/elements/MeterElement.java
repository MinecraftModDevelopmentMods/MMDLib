package zone.moddev.mc.lib.gui.model.elements;

import static zone.moddev.mc.lib.gui.model.ValueContract.NUMBERS;

import java.util.Optional;
import java.util.Set;

import zone.moddev.mc.lib.gui.WidgetTypes;
import zone.moddev.mc.lib.gui.layout.ElementLayouts;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Orientation;
import zone.moddev.mc.lib.gui.model.ValueContract;
import zone.moddev.mc.lib.gui.value.ValueType;

/**
 * {@code meter}: the compact segmented gauge.
 *
 * <p>
 * {@code orientation}: which way it runs {@code thickness}: 5 or 10 GUI pixels
 * {@code length}: its length in GUI pixels, or {@link Element#FILL}
 * {@code color}: the ARGB segment color {@code
 * segment}: segment size in pixels {@code gap}: gap between segments in pixels
 * {@code gapColor}: an optional ARGB gap color; the panel background otherwise
 */
public class MeterElement implements Element {
    private final Common common;
    private final Orientation orientation;
    private final int thickness;
    private final int length;
    private final int color;
    private final int segment;
    private final int gap;
    private final Optional<Integer> gapColor;

    public MeterElement(Common common, Orientation orientation, int thickness,
        int length, int color, int segment, int gap,
        Optional<Integer> gapColor) {
        this.common = common;
        this.orientation = orientation;
        this.thickness = thickness;
        this.length = length;
        this.color = color;
        this.segment = segment;
        this.gap = gap;
        this.gapColor = gapColor;
    }

    /** Whether the length is {@code fill}. */
    public boolean fills() {
        return length == FILL;
    }

    public int[] measure(TextMeasure text) {
        return ElementLayouts.oriented(orientation, thickness,
            fills() ? 4 : length);
    }

    public boolean stretchX() {
        return fills();
    }

    public boolean stretchY() {
        return fills();
    }

    public Set<ValueType<?>> valueKinds(WidgetTypes widgets) {
        return NUMBERS;
    }

    public void validate() {
        Element.super.validate();
        ValueContract.require(this, "a <meter>", true);
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

    public int thickness() {
        return thickness;
    }

    public int length() {
        return length;
    }

    public int color() {
        return color;
    }

    public int segment() {
        return segment;
    }

    public int gap() {
        return gap;
    }

    public Optional<Integer> gapColor() {
        return gapColor;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (object == null || getClass() != object.getClass())
            return false;
        var other = (MeterElement) object;
        return java.util.Objects.equals(common, other.common)
            && java.util.Objects.equals(orientation, other.orientation)
            && java.util.Objects.equals(thickness, other.thickness)
            && java.util.Objects.equals(length, other.length)
            && java.util.Objects.equals(color, other.color)
            && java.util.Objects.equals(segment, other.segment)
            && java.util.Objects.equals(gap, other.gap)
            && java.util.Objects.equals(gapColor, other.gapColor);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(common);
        hash = 31 * hash + java.util.Objects.hashCode(orientation);
        hash = 31 * hash + java.util.Objects.hashCode(thickness);
        hash = 31 * hash + java.util.Objects.hashCode(length);
        hash = 31 * hash + java.util.Objects.hashCode(color);
        hash = 31 * hash + java.util.Objects.hashCode(segment);
        hash = 31 * hash + java.util.Objects.hashCode(gap);
        hash = 31 * hash + java.util.Objects.hashCode(gapColor);
        return hash;
    }

    @Override
    public String toString() {
        return "MeterElement[common=" + common + ", orientation=" + orientation
            + ", thickness=" + thickness + ", length=" + length + ", color="
            + color + ", segment=" + segment + ", gap=" + gap + ", gapColor="
            + gapColor + "]";
    }
}
