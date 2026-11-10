package zone.moddev.mc.lib.gui.client;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import zone.moddev.mc.lib.gui.layout.Rect;

/** Optional capability supplied by registered element behaviors or capability adapters. */
public interface IngredientDropHandler {
    /** Pure discovery: no edits or drag-start side effects. Bounds use screen coordinates. */
    <T> List<Target<T>> targets(IngredientType<T> type, T ingredient);

    /** Stable per-element ID, drop region, and acceptance through the element's ordinary action. */
    record Target<T>(String id, Rect bounds, Predicate<T> accept) {
        public Target {
            Objects.requireNonNull(id);
            Objects.requireNonNull(bounds);
            Objects.requireNonNull(accept);
            if (id.isBlank())
                throw new IllegalArgumentException("Empty ingredient drop target ID");
        }
    }
}
