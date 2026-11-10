package zone.moddev.mc.lib.gui.model.containers;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import zone.moddev.mc.lib.gui.WidgetTypes;
import zone.moddev.mc.lib.gui.layout.*;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.model.ValueContract;
import zone.moddev.mc.lib.gui.model.elements.RadioElement;
import zone.moddev.mc.lib.gui.value.ValueType;
import zone.moddev.mc.lib.gui.value.ValueTypes;

/**
 * {@code radio-group}: radio buttons; its value is the selected index.
 *
 * <p>{@code label}: an optional caption {@code options}: the radio buttons, in order
 */
public class RadioGroupElement implements Element {
  private final Common common;
  private final Optional<Text> label;
  private final List<RadioElement> options;

  /** Copies the options. */
  public RadioGroupElement(Common common, Optional<Text> label, List<RadioElement> options) {
    options = List.copyOf(options);

    this.common = common;
    this.label = label;
    this.options = options;
  }

  @Override
  public List<Element> children() {
    return List.copyOf(options);
  }

  public int[] measure(TextMeasure text) {
    var inner = ElementLayouts.stack(children(), 2, text);
    return new int[] {
      Math.max(inner[0], label.map(text::width).orElse(0)),
      inner[1] + (label.isPresent() ? TextMeasure.LINE_HEIGHT + 2 : 0)
    };
  }

  public Map<Element, Rect> childLayout(Rect bounds, TextMeasure text) {
    int header = label.isPresent() ? TextMeasure.LINE_HEIGHT + 2 : 0;
    return ElementLayouts.stackPositions(
        children(), new Rect(0, header, bounds.width(), bounds.height() - header), 2, text);
  }

  public Set<ValueType<?>> valueKinds(WidgetTypes widgets) {
    return Set.of(ValueTypes.WHOLE);
  }

  public void validate() {
    Element.super.validate();
    ValueContract.require(this, "a <radio-group>", false);
  }

  public boolean validClickOption(int option) {
    return option >= 0 && option < options.size();
  }

  public Common common() {
    return common;
  }

  public Optional<Text> label() {
    return label;
  }

  public List<RadioElement> options() {
    return options;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) return true;
    if (object == null || getClass() != object.getClass()) return false;
    var other = (RadioGroupElement) object;
    return java.util.Objects.equals(common, other.common)
        && java.util.Objects.equals(label, other.label)
        && java.util.Objects.equals(options, other.options);
  }

  @Override
  public int hashCode() {
    int hash = 0;
    hash = 31 * hash + java.util.Objects.hashCode(common);
    hash = 31 * hash + java.util.Objects.hashCode(label);
    hash = 31 * hash + java.util.Objects.hashCode(options);
    return hash;
  }

  @Override
  public String toString() {
    return "RadioGroupElement[common=" + common + ", label=" + label + ", options=" + options + "]";
  }
}
