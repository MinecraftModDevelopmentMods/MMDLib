package zone.moddev.mc.lib.gui.runtime.component;

import java.util.List;
import java.util.Optional;

import zone.moddev.mc.lib.gui.drawer.DrawerDefinition;
import zone.moddev.mc.lib.gui.model.Edge;

/**
 * Coordinates authorized drawer sessions using only their registered placement.
 */
public final class DrawerSessions {
    private DrawerSessions() {
    }

    private static Optional<Edge> edge(DrawerSession drawer,
        List<DrawerDefinition> definitions) {
        var edges = definitions.stream()
            .filter(d -> d.type().equals(drawer.drawerType()))
            .map(d -> d.placement().edge()).distinct().toList();
        if (edges.size() > 1)
            throw new IllegalArgumentException(
                "A drawer session cannot occupy two edges: "
                    + drawer.drawerType());
        return edges.stream().findFirst();
    }

    public static void activate(DrawerSession source,
        List<DrawerSession> sessions, List<DrawerDefinition> definitions) {
        edge(source, definitions).ifPresent(edge -> {
            for (var other : sessions)
                if (other != source && edge(other, definitions)
                    .filter(edge::equals).isPresent())
                    other.closeServer();
        });
    }
}
