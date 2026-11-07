package zone.moddev.mc.lib.gui.load;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.load.ScreenReader.Node;

/** Resolves named screen templates before model validation, layout and getter binding. */
final class ScreenTemplates {
  private final Map<ResourceLocation, Node> definitions;
  private final Map<ResourceLocation, Node> resolved = new LinkedHashMap<>();
  private final LinkedHashSet<ResourceLocation> visiting = new LinkedHashSet<>();

  ScreenTemplates(Map<ResourceLocation, Node> definitions) {
    this.definitions = Map.copyOf(definitions);
  }

  static boolean flag(Node node, String attribute) {
    return "true".equals(node.attributes().get(attribute))
        || "1".equals(node.attributes().get(attribute));
  }

  Node resolve(ResourceLocation id) {
    var cached = resolved.get(id);
    if (cached != null) return cached;
    var root = definitions.get(id);
    if (!visiting.add(id)) throw error(root, "Screen inheritance cycle: " + visiting + " -> " + id);
    if (!root.name().equals("screen")) throw error(root, "Expected a <screen> root");
    var parent = root.attributes().get("extends");
    Node result = root;
    if (parent != null) {
      var parentId = ResourceLocation.parse(parent);
      if (!definitions.containsKey(parentId)) throw error(root, "Unknown base screen: " + parent);
      var base = resolve(parentId);
      var placeholders = placeholders(base);
      var fills = new LinkedHashMap<String, Node>();
      for (var child : root.children()) {
        if (!child.name().equals("fill"))
          throw error(child, "An extending screen supplies content through <fill> only");
        var name = child.attributes().get("name");
        if (fills.putIfAbsent(name, child) != null) throw error(child, "Duplicate fill: " + name);
        if (!placeholders.containsKey(name)) throw error(child, "Unknown placeholder: " + name);
      }
      // Replacing an outer placeholder also replaces its nested extension points.
      var used = new LinkedHashSet<String>();
      var children = apply(base.children(), fills, used);
      for (var entry : fills.entrySet())
        if (!used.contains(entry.getKey()))
          throw error(entry.getValue(), "Fill targets a replaced placeholder: " + entry.getKey());
      var attributes = new LinkedHashMap<>(root.attributes());
      if (!attributes.containsKey("inventory") && base.attributes().containsKey("inventory"))
        attributes.put("inventory", base.attributes().get("inventory"));
      result = new Node(root.name(), attributes, children, root.source(), root.line());
    } else {
      for (var child : root.children())
        if (child.name().equals("fill")) throw error(child, "<fill> requires extends on <screen>");
    }
    placeholders(result); // Reject ambiguous names, including newly introduced extension points.
    if (!flag(root, "abstract")) {
      for (var attribute : List.of("title", "block-entity"))
        if (!root.attributes().containsKey(attribute))
          throw error(root, "A concrete screen requires its own " + attribute);
    }
    visiting.remove(id);
    resolved.put(id, result);
    return result;
  }

  Node expand(ResourceLocation id) {
    var root = resolve(id);
    if (flag(root, "abstract")) throw error(root, "An abstract screen cannot be opened or bound");
    return root.withChildren(expand(root.children()));
  }

  private static Map<String, Node> placeholders(Node root) {
    var found = new LinkedHashMap<String, Node>();
    collect(root, found);
    return found;
  }

  private static void collect(Node node, Map<String, Node> found) {
    if (node.name().equals("placeholder")) {
      var name = node.attributes().get("name");
      if (found.putIfAbsent(name, node) != null)
        throw error(node, "Duplicate placeholder: " + name);
      if (flag(node, "required") && !node.children().isEmpty())
        throw error(node, "A required placeholder cannot also have default content: " + name);
    }
    for (var child : node.children()) collect(child, found);
  }

  private static List<Node> apply(
      List<Node> nodes, Map<String, Node> fills, java.util.Set<String> used) {
    var result = new ArrayList<Node>();
    for (var node : nodes) {
      var fill =
          node.name().equals("placeholder") ? fills.get(node.attributes().get("name")) : null;
      if (fill != null) {
        used.add(node.attributes().get("name"));
        var attributes = new LinkedHashMap<>(node.attributes());
        attributes.remove("required");
        // Keep the extension point so a further descendant can override it again.
        result.add(new Node(node.name(), attributes, fill.children(), node.source(), node.line()));
      } else result.add(node.withChildren(apply(node.children(), fills, used)));
    }
    return result;
  }

  private static List<Node> expand(List<Node> nodes) {
    var result = new ArrayList<Node>();
    for (var node : nodes) {
      if (node.name().equals("placeholder")) {
        if (flag(node, "required"))
          throw error(node, "Missing required fill: " + node.attributes().get("name"));
        result.addAll(expand(node.children()));
      } else result.add(node.withChildren(expand(node.children())));
    }
    return result;
  }

  private static ScreenException error(Node node, String message) {
    return new ScreenException(node.source(), node.line(), message);
  }
}
