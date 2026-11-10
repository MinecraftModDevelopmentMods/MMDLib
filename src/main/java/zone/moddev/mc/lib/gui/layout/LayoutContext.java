package zone.moddev.mc.lib.gui.layout;

import java.util.*;

import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.Text;
import zone.moddev.mc.lib.gui.sync.ElementStateView;

/** Per-layout inputs shared with elements, without exposing the screen layout engine. */
public final class LayoutContext implements TextMeasure {
  private final TextMeasure text;
  private final Map<Element, Integer> minimumWidths = new IdentityHashMap<>();
  private final Map<String, String> selections;
  private final ElementStateView state;
  private final Set<Element> headers = Collections.newSetFromMap(new IdentityHashMap<>());

  public LayoutContext(TextMeasure text, Map<String, String> selections) {
    this.text = text;
    this.selections = Map.copyOf(selections);
    this.state = ElementStateView.DEFAULTS;
  }

  public LayoutContext(TextMeasure text, ElementStateView state) {
    this.text = text;
    this.state = Objects.requireNonNull(state);
    selections = Map.of();
  }

  public void reserveWidth(Element element, int width) {
    minimumWidths.merge(element, width, Math::max);
  }

  public int minimumWidth(Element element, int intrinsic) {
    return Math.max(intrinsic, minimumWidths.getOrDefault(element, 0));
  }

  public ElementStateView state() {
    return state;
  }

  public int width(Text value) {
    return text.width(value);
  }

  public String selected(String id) {
    return selections.get(id);
  }

  public void reserveHeader(Element element) {
    headers.add(element);
  }

  public boolean hasHeader(Element element) {
    return headers.contains(element);
  }
}
