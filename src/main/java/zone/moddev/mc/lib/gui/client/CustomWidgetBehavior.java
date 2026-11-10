package zone.moddev.mc.lib.gui.client;

import java.util.List;
import java.util.Optional;

import zone.moddev.mc.lib.gui.WidgetAction;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.elements.CustomElement;

/**
 * Adapts existing mod widget factories to the common element input/lifecycle
 * system.
 */
final class CustomWidgetBehavior extends DefaultElementBehavior
    implements WidgetRenderer, BehaviorCapabilities {
    private final WidgetInstance widget;

    CustomWidgetBehavior(CustomElement element, ElementAccess access) {
        super(access);
        widget = WidgetRenderers.GLOBAL.require(element.type())
            .create(new WidgetAccess() {
                public CustomElement element() {
                    return element;
                }

                public Rect bounds() {
                    return access.bounds();
                }

                public Object value() {
                    return access.value();
                }

                public Object maximum() {
                    return access.maximum();
                }

                public boolean pending() {
                    return access.pending(element);
                }

                public <T> boolean submit(WidgetAction<T> action, T value) {
                    return access.submit(action, value);
                }
            });
    }

    protected Click click(double x, double y, int button) {
        return widget.mouseClicked(x, y, button);
    }

    public Object stateIdentity() {
        return widget.stateIdentity();
    }

    public void mouseMoved(double x, double y) {
        widget.mouseMoved(x, y);
    }

    public void hoverChanged(boolean hovered) {
        widget.hoverChanged(hovered);
    }

    public boolean focusable() {
        return widget.focusable();
    }

    public void setFocused(boolean focused) {
        widget.setFocused(focused);
    }

    public boolean mouseDragged(double x, double y, int button, double dx,
        double dy) {
        return widget.mouseDragged(x, y, button, dx, dy);
    }

    public boolean mouseReleased(double x, double y, int button) {
        return widget.mouseReleased(x, y, button);
    }

    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        return widget.mouseScrolled(x, y, dx, dy);
    }

    public boolean keyPressed(int key, int scan, int modifiers) {
        return widget.keyPressed(key, scan, modifiers);
    }

    public boolean keyReleased(int key, int scan, int modifiers) {
        return widget.keyReleased(key, scan, modifiers);
    }

    public boolean charTyped(char character, int modifiers) {
        return widget.charTyped(character, modifiers);
    }

    public void initialized() {
        widget.initialized();
    }

    public boolean shortcut(int key, int scan, int modifiers) {
        return widget.shortcut(key, scan, modifiers);
    }

    public List<net.minecraft.network.chat.Component> tooltip(int x, int y) {
        return widget.tooltip(x, y);
    }

    public <T> Optional<T> capability(Class<T> type) {
        return BehaviorCapabilities.find(widget, type);
    }

    public void tick() {
        widget.tick();
    }

    public void resized() {
        widget.resized();
    }

    public void visibilityChanged(boolean visible) {
        widget.visibilityChanged(visible);
    }

    public void cancelInteraction() {
        widget.cancelInteraction();
    }

    public void actionResult(boolean accepted) {
        widget.actionResult(accepted);
    }

    public void removed() {
        widget.removed();
    }

    public void render(WidgetRenderer.Context context) {
        widget.render(context);
    }
}
