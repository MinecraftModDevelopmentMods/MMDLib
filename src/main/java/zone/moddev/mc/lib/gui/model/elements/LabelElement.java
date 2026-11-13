package zone.moddev.mc.lib.gui.model.elements;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.network.chat.Component;

import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.ElementSnapshot;
import zone.moddev.mc.lib.gui.model.ProcessorVariable;
import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.machine.feature.Processor;

/**
 * {@code label}: a line of text.
 *
 * <p>
 * {@code text}: the text
 */
public class LabelElement implements Element {
    private final Common common;
    private final Text text;
    private final Optional<String> variable;
    private final int width, height;
    private final boolean translateLines;

    public LabelElement(Common common, Text text) {
        this(common, text, Optional.empty(), 0, 0, false);
    }

    public LabelElement(Common common, Text text, Optional<String> variable,
        int width, int height, boolean translateLines) {
        this.common = common;
        this.text = text;
        this.variable = variable;
        this.width = width;
        this.height = height;
        this.translateLines = translateLines;
        validateLabel();
    }

    public int[] measure(TextMeasure measure) {
        return new int[]{width > 0 ? width : measure.width(text),
            height > 0 ? height : TextMeasure.LINE_HEIGHT};
    }

    public Optional<String> variable() {
        return variable;
    }

    public void validate() {
        Element.super.validate();
        validateLabel();
    }

    private void validateLabel() {
        if (text == null && variable.isEmpty())
            throw new IllegalArgumentException(
                "Label requires text or variable");
        if (variable.isPresent() && (text != null || common.value().isPresent()
            || common.max().isPresent()))
            throw new IllegalArgumentException(
                "Label variable cannot be combined with text/value/max");
        variable.ifPresent(ProcessorVariable::parse);
        if (width < 0 || height < 0 || width > 1024 || height > 1024
            || (text == null && (width == 0 || height == 0)))
            throw new IllegalArgumentException(
                "Dynamic labels require width and height from 1 to 1024");
    }

    public void validateHostType(Class<?> owner) {
        if (variable.isPresent() && !ScreenHost.class.isAssignableFrom(owner))
            throw new IllegalArgumentException(
                "Processor-variable label requires a ScreenHost");
    }

    public void validateDefinition(Map<String, Processor<?>> processors) {
        if (variable.isEmpty())
            return;
        displayLines(ProcessorVariable.parse(variable.orElseThrow())
            .defaultValue(processors));
    }

    private Object storedValue(ScreenHost host) {
        return ProcessorVariable.parse(variable.orElseThrow()).read(host);
    }

    public void validateHost(ScreenHost host) {
        if (variable.isPresent())
            displayLines(storedValue(host));
    }

    public Optional<ElementSnapshot> snapshot(ScreenHost host) {
        return variable.map(
            ignored -> new ElementSnapshot(displayLines(storedValue(host)), 0));
    }

    public Component displayLines(Object value) {
        if (value instanceof Component component)
            return component.copy();
        if (value instanceof List<?> lines) {
            var result = Component.empty();
            for (int i = 0; i < lines.size(); i++) {
                if (i > 0)
                    result.append("\n");
                if (lines.get(i) instanceof List<?>)
                    throw new IllegalArgumentException(
                        "Nested label lines are unsupported");
                result.append(displayLines(lines.get(i)));
            }
            return result;
        }
        if (value instanceof String text && translateLines) {
            var result = Component.empty();
            var lines = text.split("\\R", -1);
            for (int i = 0; i < lines.length; i++) {
                if (i > 0)
                    result.append("\n");
                if (!lines[i].isEmpty())
                    result.append(Component.translatable(lines[i]));
            }
            return result;
        }
        if (value instanceof String || value instanceof Number
            || value instanceof Boolean)
            return Component.literal(value.toString());
        throw new IllegalArgumentException(
            "Label variable must contain text, a number, a boolean, or a list of lines");
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
        return Objects.equals(common, other.common)
            && Objects.equals(text, other.text)
            && variable.equals(other.variable) && width == other.width
            && height == other.height && translateLines == other.translateLines;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + Objects.hashCode(common);
        hash = 31 * hash + Objects.hashCode(text);
        hash = 31 * hash
            + Objects.hash(variable, width, height, translateLines);
        return hash;
    }

    @Override
    public String toString() {
        return "LabelElement[common=" + common + ", text=" + text + ",variable="
            + variable + ",width=" + width + ",height=" + height
            + ",translateLines=" + translateLines + "]";
    }
}
