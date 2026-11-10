package zone.moddev.mc.lib.gui.client;

import java.util.List;

import net.minecraft.network.chat.Component;

import zone.moddev.mc.lib.api.automation.FilterControl;

/** Adapts the existing filter-widget API to the shared element lifecycle and capture path. */
final class FilterCustomBehavior implements ElementBehavior, FilterControlPresentation {
    private final FilterWidgets.Instance instance;
    private final ElementAccess access;

    FilterCustomBehavior(FilterControl.Custom element, ElementAccess access) {
        this.access = access;
        instance = FilterWidgets.create(
            element,
            access::bounds,
            () -> FilterDrawing.configuration(access.value()).copy(),
            () -> access.pending(element),
            value -> FilterEdits.submit(access, element, 0, value));
    }

    public boolean focusable() {
        return instance != null;
    }

    public void setFocused(boolean focused) {
        if (instance != null)
            instance.focus(focused);
    }

    public Click mouseClicked(double x, double y, int button) {
        return instance != null && instance.click(x, y, button) ? Click.CAPTURE : Click.PASS;
    }

    public boolean keyPressed(int key, int scan, int modifiers) {
        return instance != null && instance.key(key, scan, modifiers);
    }

    public boolean charTyped(char c, int modifiers) {
        return instance != null && instance.character(c, modifiers);
    }

    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return instance != null && instance.drag(x, y, button, dx, dy);
    }

    public boolean mouseReleased(double x, double y, int button) {
        return instance != null && instance.release(x, y, button);
    }

    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        return instance != null && instance.scroll(x, y, dy);
    }

    public void cancelInteraction() {
        if (instance != null)
            instance.cancel();
    }

    public void removed() {
        if (instance != null)
            instance.removed();
    }

    public List<Component> tooltip(int x, int y) {
        var tip = instance == null ? null : instance.tooltip();
        return tip == null ? List.of() : List.of(tip);
    }

    public void render(ElementRenderers.Context c) {
        if (instance == null)
            c.graphics()
                .drawString(c.access().font(), "?", c.bounds().x() + 2, c.bounds().y() + 2,
                    0xffffff);
        else
            instance.render(
                c.graphics(),
                c.bounds(),
                FilterDrawing.configuration(access.value()).copy(),
                c.mouseX(),
                c.mouseY());
    }
}
