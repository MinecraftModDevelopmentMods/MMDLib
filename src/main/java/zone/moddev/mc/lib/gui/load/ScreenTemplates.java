package zone.moddev.mc.lib.gui.load;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.load.ScreenReader.Node;

/**
 * Resolves named screen templates before model validation, layout and getter
 * binding.
 */
final class ScreenTemplates {
    private final Map<ResourceLocation, Node> definitions;
    private final Map<ResourceLocation, Node> resolved = new LinkedHashMap<>();
    private final LinkedHashSet<ResourceLocation> visiting = new LinkedHashSet<>();
    private final ScreenFillGenerator generator;
    private final ScreenTags tags;

    ScreenTemplates(Map<ResourceLocation, Node> definitions) {
        this(definitions, ScreenFillGenerator.UNAVAILABLE);
    }

    ScreenTemplates(Map<ResourceLocation, Node> definitions,
        ScreenFillGenerator generator) {
        this(definitions, generator, ScreenTags.GLOBAL);
    }

    ScreenTemplates(Map<ResourceLocation, Node> definitions,
        ScreenFillGenerator generator, ScreenTags tags) {
        this.definitions = Map.copyOf(definitions);
        this.generator = generator;
        this.tags = tags;
        definitions.values().forEach(this::validateAttributes);
    }

    private void validateAttributes(Node node) {
        if (tags.is(node, ScreenTag.Role.FILL)
            || tags.is(node, ScreenTag.Role.PLACEHOLDER)) {
            boolean generated = node.attributes().containsKey("generator");
            if (generated && ResourceLocation
                .tryParse(node.attributes().get("generator")) == null)
                throw error(node, "Invalid generator='"
                    + node.attributes().get("generator") + "'");
            for (var key : node.attributes().keySet())
                if (!key.equals("name") && !key.equals("generator")
                    && !(tags.is(node, ScreenTag.Role.PLACEHOLDER)
                        && key.equals("required"))
                    && !generated)
                    throw error(node, "Unknown attribute " + key + " on <"
                        + node.name() + "> without a generator");
        }
        node.children().forEach(this::validateAttributes);
    }

    static boolean flag(Node node, String attribute) {
        return "true".equals(node.attributes().get(attribute))
            || "1".equals(node.attributes().get(attribute));
    }

    Node resolve(ResourceLocation id) {
        var cached = resolved.get(id);
        if (cached != null)
            return cached;
        var root = definitions.get(id);
        if (!visiting.add(id))
            throw error(root,
                "Screen inheritance cycle: " + visiting + " -> " + id);
        if (!tags.is(root, ScreenTag.Role.DOCUMENT))
            throw error(root, "Expected a <screen> root");
        var parent = root.attributes().get("extends");
        Node result = root;
        if (parent != null) {
            var parentId = ResourceLocation.tryParse(parent);
            if (parentId == null)
                throw error(root, "Invalid extends='" + parent + "'");
            if (!definitions.containsKey(parentId))
                throw error(root, "Unknown base screen: " + parent);
            var base = resolve(parentId);
            var placeholders = placeholders(base);
            var fills = new LinkedHashMap<String, Node>();
            for (var child : root.children()) {
                if (!tags.is(child, ScreenTag.Role.FILL))
                    throw error(child,
                        "An extending screen supplies content through <fill> only");
                var name = child.attributes().get("name");
                if (fills.putIfAbsent(name, child) != null)
                    throw error(child, "Duplicate fill: " + name);
                if (!placeholders.containsKey(name))
                    throw error(child, "Unknown placeholder: " + name);
            }
            // Replacing an outer placeholder also replaces its nested extension
            // points.
            var used = new LinkedHashSet<String>();
            var children = apply(base.children(), fills, used);
            for (var entry : fills.entrySet())
                if (!used.contains(entry.getKey()))
                    throw error(entry.getValue(),
                        "Fill targets a replaced placeholder: "
                            + entry.getKey());
            var attributes = new LinkedHashMap<>(root.attributes());
            for (var attribute : List.of("inventory", "block-entity"))
                if (!attributes.containsKey(attribute)
                    && base.attributes().containsKey(attribute))
                    attributes.put(attribute, base.attributes().get(attribute));
            result = new Node(root.name(), attributes, children, root.source(),
                root.line(), root.namespace());
        } else {
            for (var child : root.children())
                if (tags.is(child, ScreenTag.Role.FILL))
                    throw error(child, "<fill> requires extends on <screen>");
        }
        placeholders(result); // Reject ambiguous names, including newly
                              // introduced extension
                              // points.
        if (!flag(root, "abstract")) {
            for (var attribute : List.of("title", "block-entity"))
                if (!result.attributes().containsKey(attribute))
                    throw error(root,
                        "A concrete screen requires its own " + attribute);
        }
        visiting.remove(id);
        resolved.put(id, result);
        return result;
    }

    Node expand(ResourceLocation id) {
        var root = resolve(id);
        if (flag(root, "abstract"))
            throw error(root, "An abstract screen cannot be opened or bound");
        return root.withChildren(expand(id, root.children()));
    }

    private Map<String, Node> placeholders(Node root) {
        var found = new LinkedHashMap<String, Node>();
        collect(root, found);
        return found;
    }

    private void collect(Node node, Map<String, Node> found) {
        if (tags.is(node, ScreenTag.Role.PLACEHOLDER)) {
            var name = node.attributes().get("name");
            if (found.putIfAbsent(name, node) != null)
                throw error(node, "Duplicate placeholder: " + name);
            if (node.attributes().containsKey("generator")
                && !node.children().isEmpty())
                throw error(node,
                    "A generator cannot also supply literal children");
            if (flag(node, "required") && (!node.children().isEmpty()
                || node.attributes().containsKey("generator")))
                throw error(node,
                    "A required placeholder cannot also have default content: "
                        + name);
        }
        for (var child : node.children())
            collect(child, found);
    }

    private List<Node> apply(List<Node> nodes, Map<String, Node> fills,
        java.util.Set<String> used) {
        var result = new ArrayList<Node>();
        for (var node : nodes) {
            var fill = tags.is(node, ScreenTag.Role.PLACEHOLDER)
                ? fills.get(node.attributes().get("name"))
                : null;
            if (fill != null) {
                used.add(node.attributes().get("name"));
                if (fill.attributes().containsKey("generator")
                    && !fill.children().isEmpty())
                    throw error(fill,
                        "A generator cannot also supply literal children");
                var attributes = new LinkedHashMap<>(fill.attributes());
                // Keep the extension point so a further descendant can override
                // it again.
                result.add(new Node(node.name(), attributes, fill.children(),
                    fill.source(), fill.line(), node.namespace()));
            } else
                result.add(
                    node.withChildren(apply(node.children(), fills, used)));
        }
        return result;
    }

    private List<Node> expand(ResourceLocation id, List<Node> nodes) {
        var result = new ArrayList<Node>();
        for (var node : nodes) {
            if (tags.is(node, ScreenTag.Role.PLACEHOLDER)) {
                if (flag(node, "required"))
                    throw error(node, "Missing required fill: "
                        + node.attributes().get("name"));
                if (node.attributes().containsKey("generator")) {
                    var generated = generator.generate(id, node);
                    for (var child : generated)
                        rejectTemplates(child);
                    result.addAll(generated);
                } else
                    result.addAll(expand(id, node.children()));
            } else
                result.add(node.withChildren(expand(id, node.children())));
        }
        return result;
    }

    private void rejectTemplates(Node node) {
        if (Set.of(ScreenTag.Role.DOCUMENT, ScreenTag.Role.PLACEHOLDER,
            ScreenTag.Role.FILL).contains(tags.require(node).role()))
            throw error(node,
                "Generators must return ordinary screen elements");
        node.children().forEach(this::rejectTemplates);
    }

    private static ScreenException error(Node node, String message) {
        return new ScreenException(node.source(), node.line(), message);
    }
}
