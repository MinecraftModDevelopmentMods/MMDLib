package zone.moddev.mc.lib.gui.client;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Element.Custom;

/** Focus, capture and lifecycle routing, independent of vanilla's inventory key handling. */
public final class WidgetInput {
  private final List<Custom> order;
  private final Map<Custom, WidgetInstance> instances = new IdentityHashMap<>();
  private final Map<Custom, Boolean> shown = new IdentityHashMap<>();
  private final Predicate<Custom> visible;
  private final Function<Custom, Rect> bounds;
  private Custom focused, captured;
  private int capturedButton = -1, cancelledButton = -1;
  private boolean closed;

  public WidgetInput(
      List<Custom> elements,
      Function<Custom, WidgetInstance> factory,
      Predicate<Custom> visible,
      Function<Custom, Rect> bounds) {
    order = List.copyOf(elements);
    this.visible = visible;
    this.bounds = bounds;
    for (var element : order)
      instances.put(element, Objects.requireNonNull(factory.apply(element)));
    var unique = Collections.newSetFromMap(new IdentityHashMap<WidgetInstance, Boolean>());
    for (var instance : instances.values())
      if (!unique.add(instance))
        throw new IllegalArgumentException("Widget factories must return fresh instances");
  }

  public WidgetInstance instance(Custom element) {
    return instances.get(element);
  }

  public Custom focused() {
    return focused;
  }

  public boolean capturing() {
    update();
    return captured != null;
  }

  /** A new physical press ends any suppression left over from a cancelled earlier gesture. */
  public void beginClick() {
    update();
    if (captured == null) cancelledButton = -1;
  }

  public void update() {
    if (closed) return;
    for (var element : order) {
      boolean now = visible.test(element);
      Boolean previous = shown.put(element, now);
      if (!Objects.equals(previous, now)) {
        if (!now) {
          if (captured == element) cancelCapture();
          else instances.get(element).cancelInteraction();
          if (focused == element) focus(null);
        }
        instances.get(element).visibilityChanged(now);
      }
    }
  }

  public void tick() {
    update();
    if (!closed) for (var e : order) if (visible.test(e)) instances.get(e).tick();
  }

  public void blurUnless(Object element) {
    if (focused != element) focus(null);
  }

  public void clearFocus() {
    focus(null);
  }

  private void focus(Custom element) {
    if (focused == element) return;
    if (focused != null) instances.get(focused).setFocused(false);
    focused = element;
    if (focused != null) instances.get(focused).setFocused(true);
  }

  /** Called in screen hit order, after checking drawers and other elements above this one. */
  public boolean click(Custom element, double x, double y, int button) {
    update();
    if (closed || !visible.test(element)) return false;
    if (captured != null) return true;
    cancelledButton = -1;
    var instance = instances.get(element);
    var result = instance.mouseClicked(x, y, button);
    if (result == WidgetInstance.Click.PASS) return false;
    focus(instance.focusable() ? element : null);
    if (result == WidgetInstance.Click.CAPTURE) {
      captured = element;
      capturedButton = button;
    }
    return true;
  }

  public boolean drag(double x, double y, int button, double dx, double dy) {
    update();
    if (closed) return false;
    if (captured == null) return button == cancelledButton;
    if (button == capturedButton) instances.get(captured).mouseDragged(x, y, button, dx, dy);
    return true;
  }

  public boolean release(double x, double y, int button) {
    update();
    if (closed) return false;
    if (button == cancelledButton) {
      cancelledButton = -1;
      return true;
    }
    // A release belongs to the control that captured the press, not the current hover target.
    if (captured == null) return false;
    if (button == capturedButton) {
      var instance = instances.get(captured);
      captured = null;
      capturedButton = -1;
      instance.mouseReleased(x, y, button);
    }
    return true;
  }

  public boolean scroll(double x, double y, double dx, double dy) {
    update();
    if (closed) return false;
    if (captured != null) return true;
    for (var e : order.reversed())
      if (visible.test(e)
          && bounds.apply(e).contains(x, y)
          && instances.get(e).mouseScrolled(x, y, dx, dy)) return true;
    return false;
  }

  public boolean keyPressed(int key, int scan, int modifiers) {
    update();
    if (closed) return false;
    if (key == 258) { // Tab / Shift+Tab.
      cancelCapture();
      var candidates =
          order.stream().filter(visible).filter(e -> instances.get(e).focusable()).toList();
      if (candidates.isEmpty()) return false;
      int index = -1;
      for (int i = 0; i < candidates.size(); i++) if (candidates.get(i) == focused) index = i;
      int direction = (modifiers & 1) != 0 ? -1 : 1;
      focus(
          candidates.get(
              index < 0
                  ? (direction > 0 ? 0 : candidates.size() - 1)
                  : Math.floorMod(index + direction, candidates.size())));
      return true;
    }
    if (key == 256 && captured != null) {
      cancelCapture();
      return true;
    }
    if (focused == null) return false;
    boolean handled = instances.get(focused).keyPressed(key, scan, modifiers);
    if (key == 256 && !handled) focus(null);
    // Even unhandled inventory shortcuts must not reach slots while an editor has focus.
    return true;
  }

  public boolean keyReleased(int key, int scan, int modifiers) {
    update();
    if (closed) return false;
    if (focused == null) return false;
    instances.get(focused).keyReleased(key, scan, modifiers);
    return true;
  }

  public boolean charTyped(char character, int modifiers) {
    update();
    if (closed) return false;
    if (focused == null) return false;
    instances.get(focused).charTyped(character, modifiers);
    return true;
  }

  private void cancelCapture() {
    if (captured == null) return;
    cancelledButton = capturedButton;
    instances.get(captured).cancelInteraction();
    captured = null;
    capturedButton = -1;
  }

  public void resized() {
    cancelCapture();
    instances.values().forEach(WidgetInstance::resized);
  }

  public void removed() {
    if (closed) return;
    closed = true;
    cancelCapture();
    focus(null);
    instances.values().forEach(WidgetInstance::removed);
  }
}
