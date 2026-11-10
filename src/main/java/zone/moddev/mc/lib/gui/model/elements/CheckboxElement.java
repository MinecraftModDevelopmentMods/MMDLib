package zone.moddev.mc.lib.gui.model.elements;

import java.util.Set;

import zone.moddev.mc.lib.gui.WidgetTypes;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.model.ValueContract;
import zone.moddev.mc.lib.gui.value.ValueType;
import zone.moddev.mc.lib.gui.value.ValueTypes;

/**
 * {@code checkbox}: a checkbox whose value is a boolean getter.
 *
 * <p>
 * {@code text}: the caption
 */
public class CheckboxElement implements Element {
    private final Common common;
    private final Text text;

    public CheckboxElement(Common common, Text text) {
        this.common = common;
        this.text = text;
    }

    public int[] measure(TextMeasure measure) {
        return new int[]{14 + measure.width(text), 10};
    }

    public Set<ValueType<?>> valueKinds(WidgetTypes widgets) {
        return Set.of(ValueTypes.BOOLEAN);
    }

    public void validate() {
        Element.super.validate();
        ValueContract.require(this, "a <checkbox>", false);
    }

    public Common common() {
        return common;
    }

    public Text text() {
        return text;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (object == null || getClass() != object.getClass())
            return false;
        var other = (CheckboxElement) object;
        return java.util.Objects.equals(common, other.common)
            && java.util.Objects.equals(text, other.text);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(common);
        hash = 31 * hash + java.util.Objects.hashCode(text);
        return hash;
    }

    @Override
    public String toString() {
        return "CheckboxElement[common=" + common + ", text=" + text + "]";
    }
}
