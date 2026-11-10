package zone.moddev.mc.lib.gui.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Element;

/**
 * Generic discovery and stale-target checks shared by ingredient-viewer
 * integrations.
 */
public final class IngredientDrops {
    private IngredientDrops() {
    }

    public static <T> List<IngredientDropHandler.Target<T>> targets(
        ElementRenderAccess access, List<Element> elements,
        IngredientType<T> type, T ingredient) {
        var result = new ArrayList<IngredientDropHandler.Target<T>>();
        for (var element : elements) {
            for (var target : current(access, element, type, ingredient)) {
                result.add(new IngredientDropHandler.Target<>(target.id(),
                    target.bounds(), value -> {
                        for (var next : current(access, element, type, value))
                            if (next.id().equals(target.id())
                                && next.bounds().equals(target.bounds()))
                                return next.accept().test(type.snapshot(value));
                        return false;
                    }));
            }
        }
        return List.copyOf(result);
    }

    private static <T> List<IngredientDropHandler.Target<T>> current(
        ElementRenderAccess access, Element element, IngredientType<T> type,
        T ingredient) {
        if (!access.visible(element))
            return List.of();
        var handler = access.capability(element, IngredientDropHandler.class)
            .orElse(null);
        if (handler == null)
            return List.of();
        var result = new ArrayList<IngredientDropHandler.Target<T>>();
        var ids = new HashSet<String>();
        for (var target : handler.targets(type, type.snapshot(ingredient))) {
            if (!ids.add(target.id()))
                throw new IllegalArgumentException(
                    "Duplicate ingredient target: " + target.id());
            var bounds = intersection(target.bounds(), access.viewport());
            if (bounds.width() > 0 && bounds.height() > 0)
                result.add(new IngredientDropHandler.Target<>(target.id(),
                    bounds, target.accept()));
        }
        return result;
    }

    private static Rect intersection(Rect a, Rect b) {
        int x = Math.max(a.x(), b.x()), y = Math.max(a.y(), b.y());
        return new Rect(x, y, Math.max(0, Math.min(a.right(), b.right()) - x),
            Math.max(0, Math.min(a.bottom(), b.bottom()) - y));
    }
}
