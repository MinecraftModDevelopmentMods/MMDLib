package zone.moddev.mc.lib.gui.layout;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import zone.moddev.mc.lib.gui.drawer.DrawerDefinition;
import zone.moddev.mc.lib.gui.drawer.DrawerPlacement;
import zone.moddev.mc.lib.gui.model.Edge;

/** Type-agnostic packing of registered tabs on either edge and anchor. */
public final class DrawerTabs {
    private DrawerTabs() {
    }

    private static int height(List<DrawerDefinition> entries) {
        int result = 0;
        for (var entry : entries)
            result += entry.placement().height() + entry.placement().gap();
        return result - (entries.isEmpty() ? 0 : entries.getLast().placement().gap());
    }

    private static List<DrawerDefinition> select(
        List<DrawerDefinition> entries, Edge edge, DrawerPlacement.Anchor anchor) {
        return entries.stream()
            .filter(d -> d.placement().edge() == edge && d.placement().anchor() == anchor)
            .sorted(Comparator.comparingInt(d -> d.placement().order()))
            .toList();
    }

    public static int minimumHeight(List<DrawerDefinition> entries) {
        int height = 0;
        for (var edge : Edge.values()) {
            var top = select(entries, edge, DrawerPlacement.Anchor.TOP);
            var bottom = select(entries, edge, DrawerPlacement.Anchor.BOTTOM);
            height = Math.max(
                height,
                height(top)
                    + height(bottom)
                    + 2 * SideDrawerLayout.MARGIN
                    + (top.isEmpty() || bottom.isEmpty() ? 0 : top.getLast().placement().gap()));
        }
        return height;
    }

    public static Map<DrawerDefinition, Rect> place(
        List<DrawerDefinition> entries, Rect panel, int bottom,
        Predicate<DrawerDefinition> include) {
        var active = entries.stream().filter(include).toList();
        var result = new LinkedHashMap<DrawerDefinition, Rect>();
        for (var edge : Edge.values()) {
            int y = SideDrawerLayout.top(panel);
            var top = select(active, edge, DrawerPlacement.Anchor.TOP);
            for (var definition : top) {
                var p = definition.placement();
                result.put(definition, rect(panel, p, y));
                y += p.height() + p.gap();
            }
            var lower = select(active, edge, DrawerPlacement.Anchor.BOTTOM);
            int end = Math.max(SideDrawerLayout.bottom(panel, bottom), y + height(lower));
            for (var definition : lower) {
                var p = definition.placement();
                end -= p.height();
                result.put(definition, rect(panel, p, end));
                end -= p.gap();
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static Rect rect(Rect panel, DrawerPlacement p, int y) {
        return new Rect(
            p.edge() == Edge.LEFT
                ? panel.x() - p.width() + p.overlap()
                : panel.right() - p.overlap(),
            y,
            p.width(),
            p.height());
    }
}
