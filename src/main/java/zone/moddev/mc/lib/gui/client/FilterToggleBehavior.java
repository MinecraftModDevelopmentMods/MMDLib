package zone.moddev.mc.lib.gui.client;

import java.util.List;

import net.minecraft.network.chat.Component;

import zone.moddev.mc.lib.api.automation.FilterControl;

final class FilterToggleBehavior implements ElementBehavior {
    private final FilterControl.Toggle element;
    private final ElementAccess access;

    FilterToggleBehavior(FilterControl.Toggle element, ElementAccess access) {
        this.element = element;
        this.access = access;
    }

    public boolean focusable() {
        return true;
    }

    public Click mouseClicked(double x, double y, int button) {
        if (button < 0 || button > 1)
            return Click.PASS;
        FilterEdits.submit(access, element, 0, "");
        return Click.CONSUME;
    }

    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key != 257 && key != 32)
            return false;
        mouseClicked(0, 0, 0);
        return true;
    }

    public List<Component> tooltip(int x, int y) {
        return List.of(
            FilterDrawing.configuration(access.value()).getBoolean(element.id())
                ? element.selectedTooltip()
                : element.tooltip());
    }

    static void render(FilterControl.Toggle element,
        ElementRenderers.Context c) {
        var r = c.bounds();
        var g = c.graphics();
        boolean selected = FilterDrawing.configuration(c.value())
            .getBoolean(element.id());
        ElementDrawing.sprite(g,
            c.hovered() ? "button_flat_highlighted" : "button_flat", r);
        g.blitSprite(selected ? element.selectedIcon() : element.icon(),
            r.x() + 2, r.y() + 2, 10, 10);
        if (selected && element.icon().equals(element.selectedIcon()))
            g.fill(r.x() + 3, r.bottom() - 3, r.right() - 3, r.bottom() - 2,
                0xffeeeeee);
    }
}
