package zone.moddev.mc.lib.common.loading;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import net.minecraft.resources.ResourceLocation;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import zone.moddev.mc.lib.common.types.CreativeTabDefinition;

/**
 * Strict XML front end for the same definition used by JSON and Java builders.
 */
public final class CreativeTabXml {
    public static final String NAMESPACE = "https://moddev.zone/mmdlib/creative-tab";
    private CreativeTabXml() {
    }

    public static CreativeTabDefinition read(Path path) {
        try (var input = Files.newInputStream(path)) {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var root = factory.newDocumentBuilder().parse(input)
                .getDocumentElement();
            if (!"creative-tab".equals(root.getLocalName())
                || !NAMESPACE.equals(root.getNamespaceURI()))
                throw new IllegalArgumentException(
                    "Expected creative-tab in " + NAMESPACE);
            attributes(root, Set.of("version", "title", "title-fallback",
                "icon", "search-bar", "default-for-mod", "include-mod-items"));
            if (!"1".equals(root.getAttribute("version")))
                throw new IllegalArgumentException("Expected version=1");
            var contents = new ArrayList<CreativeTabDefinition.Entry>();
            var before = new ArrayList<ResourceLocation>();
            var after = new ArrayList<ResourceLocation>();
            var exclusions = new ArrayList<ResourceLocation>();
            var excludedTags = new ArrayList<ResourceLocation>();
            for (var node = root.getFirstChild(); node != null; node = node
                .getNextSibling()) {
                if (node instanceof Element child) {
                    if (!NAMESPACE.equals(child.getNamespaceURI()))
                        throw new IllegalArgumentException(
                            "Unexpected child namespace");
                    boolean exclusion = "exclude".equals(child.getLocalName());
                    attributes(child,
                        exclusion ? Set.of("id", "tag") : Set.of("id"));
                    for (var nested = child
                        .getFirstChild(); nested != null; nested = nested
                            .getNextSibling())
                        if (nested instanceof Element || !ignorable(nested))
                            throw new IllegalArgumentException(
                                "Entries must be empty elements");
                    if (exclusion) {
                        if (child.hasAttribute("id") == child
                            .hasAttribute("tag"))
                            throw new IllegalArgumentException(
                                "Exclude requires exactly one of id or tag");
                        if (child.hasAttribute("tag")) {
                            excludedTags.add(ResourceLocation
                                .parse(optional(child, "tag").orElseThrow()));
                            continue;
                        }
                    }
                    var id = ResourceLocation.parse(optional(child, "id")
                        .orElseThrow(() -> new IllegalArgumentException(
                            "Entry requires id")));
                    switch (child.getLocalName()) {
                        case "item" -> contents
                            .add(new CreativeTabDefinition.ItemEntry(id));
                        case "tag" -> contents
                            .add(new CreativeTabDefinition.TagEntry(id));
                        case "material" -> contents
                            .add(new CreativeTabDefinition.MaterialEntry(id));
                        case "before" -> before.add(id);
                        case "after" -> after.add(id);
                        case "exclude" -> exclusions.add(id);
                        default -> throw new IllegalArgumentException(
                            "Unknown entry: " + child.getLocalName());
                    }
                } else if (!ignorable(node))
                    throw new IllegalArgumentException(
                        "Unexpected creative-tab text");
            }
            return new CreativeTabDefinition(optional(root, "title"),
                optional(root, "title-fallback"),
                optional(root, "icon").map(ResourceLocation::parse), contents,
                before, after, bool(root, "search-bar"),
                bool(root, "default-for-mod"), bool(root, "include-mod-items"),
                exclusions, excludedTags);
        } catch (Exception error) {
            throw new IllegalArgumentException(path + ": " + error.getMessage(),
                error);
        }
    }

    private static boolean ignorable(Node node) {
        return node.getNodeType() == Node.COMMENT_NODE
            || node.getNodeType() == Node.TEXT_NODE
                && node.getTextContent().isBlank();
    }

    private static void attributes(Element element, Set<String> allowed) {
        var attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            var attr = attrs.item(i);
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI
                .equals(attr.getNamespaceURI()))
                continue;
            if (attr.getNamespaceURI() != null
                || !allowed.contains(attr.getNodeName()))
                throw new IllegalArgumentException(
                    "Unknown attribute: " + attr.getNodeName());
        }
    }

    private static Optional<String> optional(Element element, String name) {
        if (!element.hasAttribute(name))
            return Optional.empty();
        var text = element.getAttribute(name).trim();
        if (text.isEmpty())
            throw new IllegalArgumentException("Empty attribute: " + name);
        return Optional.of(text);
    }

    private static boolean bool(Element element, String name) {
        var value = optional(element, name).orElse("false");
        if (!value.equals("true") && !value.equals("false"))
            throw new IllegalArgumentException("Expected boolean: " + name);
        return Boolean.parseBoolean(value);
    }
}
