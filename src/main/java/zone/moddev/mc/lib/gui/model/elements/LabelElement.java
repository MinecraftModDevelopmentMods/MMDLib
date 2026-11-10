package zone.moddev.mc.lib.gui.model.elements;

import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Text;

/**
 * {@code label}: a line of text.
 *
 * <p>
 * {@code text}: the text
 */
public class LabelElement implements Element {
    private final Common common;
    private final Text text;

    public LabelElement(Common common, Text text) {
        this.common = common;
        this.text = text;
    }

    public int[] measure(TextMeasure measure) {
        return new int[]{measure.width(text), TextMeasure.LINE_HEIGHT};
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
        var other = (LabelElement) object;
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
        return "LabelElement[common=" + common + ", text=" + text + "]";
    }
}
