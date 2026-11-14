package zone.moddev.mc.lib.gui.client;

import static zone.moddev.mc.lib.gui.client.ElementDrawing.caption;
import static zone.moddev.mc.lib.gui.client.ElementDrawing.sprite;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.elements.CheckboxElement;
import zone.moddev.mc.lib.gui.model.elements.LabelElement;
import zone.moddev.mc.lib.gui.model.elements.RadioElement;

/**
 * Text and small selection controls. State comes only from synchronized display
 * values.
 */
public final class TextControlRenderer {
    private TextControlRenderer() {
    }

    public static void label(LabelElement element, ElementRenderers.Context c) {
        if (element.variable().isEmpty()) {
            caption(c, element.text(), c.bounds().x(), c.bounds().y());
            return;
        }
        if (c.value() == null)
            return;
        var text = element.displayLines(c.value());
        var r = c.bounds();
        c.graphics().enableScissor(r.x(), r.y(), r.right(), r.bottom());
        try {
            int y = r.y();
            for (var line : c.access().font().split(text, r.width())) {
                if (y + c.access().font().lineHeight > r.bottom())
                    break;
                c.graphics().drawString(c.access().font(), line, r.x(), y,
                    0xff404040, false);
                y += c.access().font().lineHeight + 2;
            }
        } finally {
            c.graphics().disableScissor();
        }
    }

    public static void checkbox(CheckboxElement element,
        ElementRenderers.Context c) {
        var r = c.bounds();
        sprite(c.graphics(),
            Boolean.TRUE.equals(c.value()) ? "checkbox_selected" : "checkbox",
            new Rect(r.x(), r.y(), 10, 10));
        caption(c, element.text(), r.x() + 14, r.y() + 1);
    }

    public static boolean selected(RadioElement element,
        ElementRenderAccess access) {
        return OptionSelection.parent(element, access)
            .map(selection -> selection.selected(element)).orElse(false);
    }

    public static void radio(RadioElement element, ElementRenderers.Context c) {
        var r = c.bounds();
        sprite(c.graphics(),
            selected(element, c.access()) ? "radio_selected" : "radio",
            new Rect(r.x(), r.y(), 10, 10));
        caption(c, element.text(), r.x() + 14, r.y() + 1);
    }
}
