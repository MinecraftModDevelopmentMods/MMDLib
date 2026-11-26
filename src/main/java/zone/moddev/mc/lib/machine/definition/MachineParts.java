package zone.moddev.mc.lib.machine.definition;

import static zone.moddev.mc.lib.machine.definition.MachineDefinitions.error;
import static zone.moddev.mc.lib.machine.definition.MachineDefinitions.number;
import static zone.moddev.mc.lib.machine.definition.MachineDefinitions.optional;
import static zone.moddev.mc.lib.machine.definition.MachineDefinitions.required;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import zone.moddev.mc.lib.gui.load.ScreenReader.Node;

/**
 * Lowers compact XML declarations into the existing storage and screen model.
 */
final class MachineParts {
    private MachineParts() {
    }

    static Node expand(Node machine, Node base) {
        var parts = optional(machine, "parts");
        if (parts == null)
            return machine;
        if (!parts.namespace().equals(machine.namespace())
            || !parts.attributes().isEmpty())
            throw error(parts, "Expected <parts> with no attributes");
        if (optional(machine, "layout") != null)
            throw error(parts,
                "A machine cannot declare both parts and layout");

        var children = new ArrayList<>(machine.children());
        int position = children.indexOf(parts);
        children.remove(position);
        var layout = new ArrayList<Node>();
        var items = new ArrayList<Node>();
        var tanks = new ArrayList<Node>();
        Node energy = null;
        for (var part : parts.children()) {
            if (!part.namespace().equals(parts.namespace())
                || !part.children().isEmpty())
                throw error(part, "Expected a machine part without children");
            switch (part.name()) {
                case "inventory" -> {
                    var attrs = new LinkedHashMap<>(part.attributes());
                    var columns = attrs.remove("columns");
                    attrs.putIfAbsent("count", "1");
                    items.add(node(part, "slots", attrs, List.of()));
                    layout.add(reference(part, required(part, "id"), "columns",
                        columns));
                }
                case "tank", "tanks" -> {
                    var attrs = new LinkedHashMap<>(part.attributes());
                    var tooltip = attrs.remove("tooltip");
                    tanks.add(node(part, part.name(), attrs, List.of()));
                    String id = required(part, "id");
                    if (part.name().equals("tanks")) {
                        int count = (int) number(part, "count", 1, 16);
                        for (int i = 0; i < count; i++)
                            layout.add(reference(part, id + "/" + i, "tooltip",
                                tooltip));
                    } else
                        layout.add(reference(part, id, "tooltip", tooltip));
                }
                case "processor" -> {
                    children.add(position++, part);
                    layout.add(node(part, "processor",
                        Map.of("ref", required(part, "id")), List.of()));
                }
                case "energy" -> {
                    if (!part.attributes().isEmpty()) {
                        var attrs = new LinkedHashMap<>(part.attributes());
                        attrs.putIfAbsent("id", "energy");
                        energy = node(part, "energy", attrs, List.of());
                    }
                    layout.add(node(part, "energy", Map.of(), List.of()));
                }
                default ->
                    throw error(part, "Unknown machine part " + part.name());
            }
        }
        children.add(node(parts, "layout", Map.of(), layout));
        if (!items.isEmpty() || !tanks.isEmpty() || energy != null) {
            var storage = optional(machine, "storage");
            var inherited = base == null ? null : optional(base, "storage");
            var groups = new ArrayList<>(
                storage == null ? List.<Node>of() : storage.children());
            append(groups, storage, inherited, "items", items);
            append(groups, storage, inherited, "fluids", tanks);
            if (energy != null)
                groups.add(energy);
            if (storage != null)
                children.remove(storage);
            children.add(node(storage == null ? parts : storage, "storage",
                storage == null ? Map.of() : storage.attributes(), groups));
        }
        return node(machine, "machine", machine.attributes(), children);
    }

    private static Node reference(Node part, String id, String option,
        String value) {
        var attrs = value == null
            ? Map.of("ref", id)
            : Map.of("ref", id, option, value);
        return node(part, part.name().equals("tanks") ? "tank" : part.name(),
            attrs, List.of());
    }

    private static void append(List<Node> groups, Node storage, Node base,
        String name, List<Node> parts) {
        if (parts.isEmpty())
            return;
        var current = storage == null ? null : optional(storage, name);
        if (current != null) {
            var children = new ArrayList<>(current.children());
            children.addAll(parts);
            groups.set(groups.indexOf(current),
                node(current, name, current.attributes(), children));
        } else {
            var inherited = base == null ? null : optional(base, name);
            String id = inherited == null
                ? name
                : inherited.attributes().getOrDefault("id", name);
            groups.add(node(parts.getFirst(), name, Map.of("id", id), parts));
        }
    }

    private static Node node(Node origin, String name,
        Map<String, String> attrs, List<Node> children) {
        return new Node(name, Map.copyOf(attrs), List.copyOf(children),
            origin.source(), origin.line(), origin.namespace());
    }
}
