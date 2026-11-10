package zone.moddev.mc.lib.gui.client;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.elements.CustomElement;

/**
 * Compatibility facade using the same generic input router as built-in
 * elements.
 */
public final class WidgetInput
    extends
        ElementInput<CustomElement, WidgetInstance> {
    public WidgetInput(List<CustomElement> elements,
        Function<CustomElement, WidgetInstance> factory,
        Predicate<CustomElement> visible,
        Function<CustomElement, Rect> bounds) {
        super(elements, factory, visible, bounds);
    }
}
