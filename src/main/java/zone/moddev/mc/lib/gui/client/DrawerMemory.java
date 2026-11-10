package zone.moddev.mc.lib.gui.client;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;

import zone.moddev.mc.lib.gui.model.Edge;

/**
 * Connection-scoped storage; each drawer interprets and restores its own
 * preference.
 */
final class DrawerMemory {
    private record Preference(String id, Drawer.Selection selection) {
    }

    private static java.lang.ref.WeakReference<Object> connection = new java.lang.ref.WeakReference<>(
        null);
    private static final Map<Edge, Preference> remembered = new EnumMap<>(
        Edge.class);
    private final Map<Edge, Preference> restoring = new EnumMap<>(Edge.class);
    private int ticks;

    DrawerMemory() {
        Object current = Minecraft.getInstance().getConnection();
        if (connection.get() != current) {
            connection = new java.lang.ref.WeakReference<>(current);
            remembered.clear();
        }
        restoring.putAll(remembered);
    }

    static void disconnected() {
        connection.clear();
        remembered.clear();
    }

    void userInput() {
        restoring.clear();
    }

    void intent(Drawer drawer, boolean open, Drawer.Selection selection) {
        if (open)
            remembered.put(drawer.edge(),
                new Preference(drawer.id(), selection));
        else
            remembered.remove(drawer.edge());
    }

    void restore(List<Drawer> drawers) {
        if (++ticks > 100) {
            restoring.clear();
            return;
        }
        for (var edge : Edge.values()) {
            var preference = restoring.get(edge);
            if (preference == null)
                continue;
            var drawer = find(drawers, edge, preference.id());
            if (drawer == null || drawer.restore(preference.selection()))
                restoring.remove(edge);
        }
    }

    void capture(List<Drawer> drawers) {
        for (var edge : Edge.values()) {
            if (restoring.containsKey(edge) || drawers.stream()
                .anyMatch(d -> d.edge() == edge && d.pending()))
                continue;
            var open = drawers.stream()
                .filter(d -> d.edge() == edge && d.available() && d.open())
                .findFirst();
            if (open.isPresent()) {
                var drawer = open.get();
                remembered.put(edge,
                    new Preference(drawer.id(), drawer.selection()));
            } else {
                var previous = remembered.get(edge);
                var drawer = previous == null
                    ? null
                    : find(drawers, edge, previous.id());
                // Missing equipment/features on this machine must not erase the
                // connection
                // preference.
                if (previous == null || drawer != null && drawer.available())
                    remembered.remove(edge);
            }
        }
    }

    private Drawer find(List<Drawer> drawers, Edge edge, String id) {
        return drawers.stream()
            .filter(d -> d.edge() == edge && d.id().equals(id)).findFirst()
            .orElse(null);
    }
}
