package zone.moddev.mc.lib.gui.client;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Element;

/**
 * Focus, capture and lifecycle routing, independent of vanilla's inventory key
 * handling. Call {@link #update()} once before routing an input event, tick or
 * frame. All operations in that event share its visibility snapshot.
 */
public class ElementInput<E extends Element, B extends ElementBehavior> {
    private final List<E> order;
    private final Map<E, B> instances = new IdentityHashMap<>();
    private final Map<E, Boolean> shown = new IdentityHashMap<>();
    private final Predicate<E> visible;
    private final Function<E, Rect> bounds;
    private final BiPredicate<Double, Double> hitArea;
    private final BiConsumer<E, Boolean> hoverListener;
    private E focused, captured, hovered;
    private double mouseX, mouseY;
    private boolean pointerKnown;
    private int capturedButton = -1, cancelledButton = -1;
    private boolean closed;

    public ElementInput(List<E> elements, Function<E, B> factory,
        Predicate<E> visible, Function<E, Rect> bounds) {
        this(elements, factory, visible, bounds, (x, y) -> true,
            (element, entered) -> {
            });
    }

    public ElementInput(List<E> elements, Function<E, B> factory,
        Predicate<E> visible, Function<E, Rect> bounds,
        BiPredicate<Double, Double> hitArea,
        BiConsumer<E, Boolean> hoverListener) {
        order = List.copyOf(elements);
        this.visible = visible;
        this.bounds = bounds;
        this.hitArea = Objects.requireNonNull(hitArea);
        this.hoverListener = Objects.requireNonNull(hoverListener);
        for (var element : order)
            instances.put(element,
                Objects.requireNonNull(factory.apply(element)));
        var unique = Collections
            .newSetFromMap(new IdentityHashMap<Object, Boolean>());
        for (var instance : instances.values())
            if (!unique.add(Objects.requireNonNull(instance.stateIdentity())))
                throw new IllegalArgumentException(
                    "Element factories must return fresh instances");
    }

    public B instance(E element) {
        return instances.get(element);
    }

    public void mouseMoved(double x, double y) {
        hover(x, y);
        if (!closed)
            for (var element : order)
                if (isShown(element))
                    instances.get(element).mouseMoved(x, y);
    }

    /**
     * Sample the pointer each frame without synthesizing mouse-movement events.
     */
    public void hover(double x, double y) {
        if (closed)
            return;
        pointerKnown = true;
        mouseX = x;
        mouseY = y;
        refreshHover();
    }

    public E hovered() {
        return hovered;
    }

    /**
     * Leave before disposal or suspension; a later pointer sample can enter
     * again.
     */
    public void clearHover() {
        pointerKnown = false;
        changeHover(null);
    }

    private void refreshHover() {
        if (closed)
            return;
        changeHover(pointerKnown ? hit(mouseX, mouseY) : null);
    }

    /** Topmost visible hit without changing pointer or focus state. */
    public E hit(double x, double y) {
        var overlay = overlayOwner();
        if (overlay != null)
            return hitArea.test(x, y)
                && (bounds.apply(overlay).contains(x, y) || instances
                    .get(overlay).overlayBounds().orElseThrow().contains(x, y))
                        ? overlay
                        : null;
        if (!closed && hitArea.test(x, y))
            for (var element : order.reversed())
                if (isShown(element) && bounds.apply(element).contains(x, y))
                    return element;
        return null;
    }

    public E overlayOwner() {
        if (!closed)
            for (var element : order.reversed())
                if (isShown(element)
                    && instances.get(element).overlayBounds().isPresent())
                    return element;
        return null;
    }

    private void changeHover(E next) {
        if (next == hovered)
            return;
        var previous = hovered;
        hovered = next;
        if (previous != null) {
            instances.get(previous).hoverChanged(false);
            hoverListener.accept(previous, false);
        }
        if (next != null && !closed && hovered == next) {
            instances.get(next).hoverChanged(true);
            hoverListener.accept(next, true);
        }
    }

    public E focused() {
        return focused;
    }

    public boolean capturing() {
        return captured != null;
    }

    /**
     * A new physical press ends any suppression left over from a cancelled
     * earlier gesture.
     */
    public void beginClick() {
        if (captured == null)
            cancelledButton = -1;
    }

    public void update() {
        if (closed)
            return;
        for (var element : order) {
            boolean now = visible.test(element);
            Boolean previous = shown.put(element, now);
            if (!Objects.equals(previous, now)) {
                if (!now) {
                    if (captured == element)
                        cancelCapture();
                    else
                        instances.get(element).cancelInteraction();
                    if (focused == element)
                        focus(null);
                }
                instances.get(element).visibilityChanged(now);
            }
        }
        refreshHover();
    }

    private boolean isShown(E element) {
        return Boolean.TRUE.equals(shown.get(element));
    }

    public void tick() {
        if (!closed)
            for (var e : order)
                if (isShown(e))
                    instances.get(e).tick();
    }

    public void blurUnless(Object element) {
        if (focused != element)
            focus(null);
    }

    public void clearFocus() {
        focus(null);
    }

    /**
     * Routes a physical press through visible hits, last element first. Hosts
     * handle overlays before this call and may fall back to native input when
     * it returns false. Capture consumes presses even outside the viewport; a
     * missed or unhandled press clears focus.
     */
    public boolean click(double x, double y, int button) {
        if (closed)
            return false;
        if (captured != null)
            return true;
        beginClick();
        var overlay = overlayOwner();
        if (overlay != null) {
            if (hit(x, y) == overlay)
                click(overlay, x, y, button);
            else
                instances.get(overlay).dismissOverlay();
            return true;
        }
        if (hitArea.test(x, y))
            for (var element : order.reversed()) {
                if (!isShown(element) || !bounds.apply(element).contains(x, y))
                    continue;
                blurUnless(element);
                if (click(element, x, y, button))
                    return true;
            }
        clearFocus();
        return false;
    }

    /**
     * Cancel the current gesture and focus when a fragment closes or its
     * viewport moves.
     */
    public void cancelInteraction() {
        if (focused != null && focused != captured)
            instances.get(focused).cancelInteraction();
        cancelCapture();
        focus(null);
    }

    private void focus(E element) {
        if (focused == element)
            return;
        if (focused != null)
            instances.get(focused).setFocused(false);
        focused = element;
        if (focused != null)
            instances.get(focused).setFocused(true);
    }

    /**
     * Called in screen hit order, after checking drawers and other elements
     * above this one.
     */
    public boolean click(E element, double x, double y, int button) {
        if (closed || !isShown(element))
            return false;
        if (captured != null)
            return true;
        cancelledButton = -1;
        var instance = instances.get(element);
        var result = instance.mouseClicked(x, y, button);
        if (result == ElementBehavior.Click.PASS)
            return false;
        focus(instance.focusable() ? element : null);
        if (result == ElementBehavior.Click.CAPTURE) {
            captured = element;
            capturedButton = button;
        }
        return true;
    }

    public boolean drag(double x, double y, int button, double dx, double dy) {
        if (closed)
            return false;
        if (captured == null)
            return button == cancelledButton;
        if (button == capturedButton)
            instances.get(captured).mouseDragged(x, y, button, dx, dy);
        return true;
    }

    public boolean release(double x, double y, int button) {
        if (closed)
            return false;
        if (button == cancelledButton) {
            cancelledButton = -1;
            return true;
        }
        // A release belongs to the control that captured the press, not the
        // current hover target.
        if (captured == null)
            return false;
        if (button == capturedButton) {
            var instance = instances.get(captured);
            captured = null;
            capturedButton = -1;
            instance.mouseReleased(x, y, button);
        }
        return true;
    }

    public boolean scroll(double x, double y, double dx, double dy) {
        if (closed)
            return false;
        if (captured != null)
            return true;
        var overlay = overlayOwner();
        if (overlay != null) {
            instances.get(overlay).mouseScrolled(x, y, dx, dy);
            return true;
        }
        if (!hitArea.test(x, y))
            return false;
        for (var e : order.reversed())
            if (isShown(e) && bounds.apply(e).contains(x, y)
                && instances.get(e).mouseScrolled(x, y, dx, dy))
                return true;
        return false;
    }

    public boolean keyPressed(int key, int scan, int modifiers) {
        if (closed)
            return false;
        var overlay = overlayOwner();
        if (overlay != null && key == 256) {
            instances.get(overlay).dismissOverlay();
            return true;
        }
        if (key == 258) { // Tab / Shift+Tab.
            cancelCapture();
            var candidates = order.stream().filter(this::isShown)
                .filter(e -> instances.get(e).focusable()).toList();
            if (candidates.isEmpty())
                return false;
            int index = -1;
            for (int i = 0; i < candidates.size(); i++)
                if (candidates.get(i) == focused)
                    index = i;
            int direction = (modifiers & 1) != 0 ? -1 : 1;
            focus(candidates.get(index < 0
                ? (direction > 0 ? 0 : candidates.size() - 1)
                : Math.floorMod(index + direction, candidates.size())));
            return true;
        }
        if (key == 256 && captured != null) {
            cancelCapture();
            return true;
        }
        if (focused == null) {
            for (var e : order)
                if (isShown(e)
                    && instances.get(e).shortcut(key, scan, modifiers))
                    return true;
            return false;
        }
        boolean handled = instances.get(focused).keyPressed(key, scan,
            modifiers);
        if (key == 256 && !handled)
            focus(null);
        // Even unhandled inventory shortcuts must not reach slots while an
        // editor has focus.
        return true;
    }

    public boolean keyReleased(int key, int scan, int modifiers) {
        if (closed)
            return false;
        if (focused == null)
            return false;
        instances.get(focused).keyReleased(key, scan, modifiers);
        return true;
    }

    public boolean charTyped(char character, int modifiers) {
        if (closed)
            return false;
        if (focused == null)
            return false;
        instances.get(focused).charTyped(character, modifiers);
        return true;
    }

    private void cancelCapture() {
        if (captured == null)
            return;
        cancelledButton = capturedButton;
        instances.get(captured).cancelInteraction();
        captured = null;
        capturedButton = -1;
    }

    public void resized() {
        if (closed)
            return;
        cancelCapture();
        instances.values().forEach(ElementBehavior::resized);
        refreshHover();
    }

    public void removed() {
        if (closed)
            return;
        closed = true;
        clearHover();
        cancelCapture();
        focus(null);
        instances.values().forEach(ElementBehavior::removed);
    }

    public void initialized() {
        if (!closed)
            for (var element : order)
                instances.get(element).initialized();
    }
}
