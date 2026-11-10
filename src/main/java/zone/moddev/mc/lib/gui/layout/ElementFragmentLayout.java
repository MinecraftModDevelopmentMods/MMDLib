package zone.moddev.mc.lib.gui.layout;

import java.util.*;
import java.util.function.Predicate;

import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.containers.SelectiveContainer;
import zone.moddev.mc.lib.gui.sync.ElementStateView;

/** A reusable element tree measured and positioned independently of a machine screen. */
public record ElementFragmentLayout(List<Element> elements, Map<Element, Rect> bounds, int height) {
  public static ElementFragmentLayout of(Element root, int width, TextMeasure text) {
    return of(root, width, text, ElementStateView.DEFAULTS);
  }

  public static ElementFragmentLayout of(
      Element root, int width, TextMeasure text, ElementStateView state) {
    var context = new LayoutContext(text, state);
    prepare(root, context, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
    return arrange(root, width, context);
  }

  private static void prepare(
      Element element, LayoutContext context, Set<Element> seen, int depth) {
    if (depth > 8 || seen.size() >= 128 || !seen.add(element))
      throw new IllegalArgumentException("Invalid/oversized fragment tree");
    element.prepareLayout(context);
    for (var child : element.children()) prepare(child, context, seen, depth + 1);
  }

  /** Parent selection and host visibility compose before viewport clipping. */
  public Map<Element, Boolean> visibility(ElementStateView state, Predicate<Element> allowed) {
    var shown = new IdentityHashMap<Element, Boolean>();
    for (var element : elements) {
      boolean visible = shown.getOrDefault(element, true) && allowed.test(element);
      shown.put(element, visible);
      for (var child : element.children())
        shown.put(
            child,
            visible
                && (!(element instanceof SelectiveContainer selective)
                    || selective.childVisible(child, state)));
    }
    return Collections.unmodifiableMap(shown);
  }

  private static ElementFragmentLayout arrange(Element root, int width, TextMeasure text) {
    if (width < 1) throw new IllegalArgumentException("Fragment width");
    var bounds = new IdentityHashMap<Element, Rect>();
    var elements = new ArrayList<Element>();
    int[] size = root.measure(text, width);
    if (size.length != 2 || size[0] < 0 || size[1] < 0)
      throw new IllegalArgumentException("Fragment size");
    place(
        root,
        new Rect(0, 0, root.stretchX() ? width : Math.min(width, size[0]), size[1]),
        text,
        elements,
        bounds,
        0);
    return new ElementFragmentLayout(
        List.copyOf(elements), Collections.unmodifiableMap(bounds), size[1]);
  }

  private static void place(
      Element element,
      Rect rect,
      TextMeasure text,
      List<Element> elements,
      Map<Element, Rect> bounds,
      int depth) {
    if (depth > 8 || elements.size() >= 128 || bounds.containsKey(element))
      throw new IllegalArgumentException("Invalid/oversized fragment tree");
    bounds.put(element, rect);
    elements.add(element);
    var children = element.childLayout(new Rect(0, 0, rect.width(), rect.height()), text);
    for (var child : element.children()) {
      var position = Objects.requireNonNull(children.get(child), "Missing fragment child bounds");
      place(child, position.offset(rect.x(), rect.y()), text, elements, bounds, depth + 1);
    }
  }
}
