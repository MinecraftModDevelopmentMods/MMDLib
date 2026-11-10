package zone.moddev.mc.lib.gui.load;

import java.io.StringReader;
import java.io.StringWriter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ls.DOMImplementationLS;

/** Builds the strict runtime schema from registered declarations, entirely in memory. */
final class ScreenSchemas {
    private static final String XS = XMLConstants.W3C_XML_SCHEMA_NS_URI;

    private static final class Library {
        static final Document XML = load();
    }

    private ScreenSchemas() {
    }

    static String name(String name) {
        if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_.-]*"))
            throw new IllegalArgumentException("Invalid XML name: " + name);
        return name;
    }

    static Element xs(Document doc, String local) {
        return doc.createElementNS(XS, "xs:" + local);
    }

    static String reference(QName name, Map<String, String> prefixes) {
        var prefix = prefixes.get(name.getNamespaceURI());
        if (prefix == null)
            throw new IllegalArgumentException(
                "No registered tags in namespace " + name.getNamespaceURI());
        return prefix + ":" + name.getLocalPart();
    }

    private static DocumentBuilderFactory factory() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }

    private static Document load() {
        try (var input = ScreenSchemas.class.getResourceAsStream("/mmdlib/schema/screen.xsd")) {
            if (input == null)
                throw new IllegalStateException("Missing screen schema library");
            return factory().newDocumentBuilder().parse(input);
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot read screen schema library", failure);
        }
    }

    static <T> ScreenTag<T> builtin(
        String name,
        Class<T> type,
        Function<ScreenXml, T> parser,
        ScreenTag.Role role,
        String... groups) {
        Element definition = null;
        for (var node = Library.XML.getDocumentElement().getFirstChild(); node != null; node = node
            .getNextSibling())
            if (node instanceof Element e
                && e.getLocalName().equals("element")
                && e.getAttribute("name").equals(name))
                definition = e;
        if (definition == null)
            throw new IllegalArgumentException("Missing built-in XML declaration: " + name);
        var template = definition;
        return new ScreenTag<>(
            type,
            parser,
            Set.of(groups),
            role,
            (doc, prefixes) -> (Element) doc.importNode(template, true));
    }

    static Schema compile(Map<QName, ScreenTag<?>> tags) {
        try {
            var namespaces = new TreeSet<String>();
            namespaces.add(ScreenReader.NAMESPACE);
            tags.keySet().forEach(name -> namespaces.add(name.getNamespaceURI()));
            var prefixes = new LinkedHashMap<String, String>();
            prefixes.put(ScreenReader.NAMESPACE, "ui");
            for (var namespace : namespaces)
                prefixes.putIfAbsent(namespace, "mod" + prefixes.size());
            var locations = new HashMap<String, String>();
            for (var namespace : namespaces)
                locations.put(namespace, "memory:/screen-" + prefixes.get(namespace) + ".xsd");
            var documents = new LinkedHashMap<String, Document>();
            for (var namespace : namespaces) {
                var doc = factory().newDocumentBuilder().newDocument();
                var schema = xs(doc, "schema");
                doc.appendChild(schema);
                schema.setAttribute("targetNamespace", namespace);
                schema.setAttribute("elementFormDefault", "qualified");
                schema.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns",
                    ScreenReader.NAMESPACE);
                schema.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:xs", XS);
                prefixes.forEach(
                    (uri, prefix) -> schema.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI,
                        "xmlns:" + prefix, uri));
                for (var imported : namespaces)
                    if (!imported.equals(namespace)) {
                        var declaration = xs(doc, "import");
                        declaration.setAttribute("namespace", imported);
                        declaration.setAttribute("schemaLocation", locations.get(imported));
                        schema.appendChild(declaration);
                    }
                documents.put(namespace, doc);
            }
            var base = documents.get(ScreenReader.NAMESPACE);
            var root = base.getDocumentElement();
            // Shared types are a library. Global tag declarations and all placement groups come
            // from
            // registrations.
            for (var node = Library.XML.getDocumentElement()
                .getFirstChild(); node != null; node = node.getNextSibling())
                if (node instanceof Element e
                    && !Set.of("element", "group").contains(e.getLocalName()))
                    root.appendChild(base.importNode(e, true));
            var groups = new TreeMap<String, List<QName>>();
            tags.forEach(
                (name, tag) -> tag.groups()
                    .forEach(
                        group -> groups.computeIfAbsent(group, key -> new ArrayList<>())
                            .add(name)));
            groups.forEach(
                (name, members) -> {
                    var group = xs(base, "group");
                    group.setAttribute("name", name);
                    var choice = xs(base, "choice");
                    group.appendChild(choice);
                    root.appendChild(group);
                    members.stream()
                        .sorted(Comparator.comparing(QName::toString))
                        .forEach(
                            member -> {
                                var ref = xs(base, "element");
                                ref.setAttribute("ref", reference(member, prefixes));
                                choice.appendChild(ref);
                            });
                });
            tags.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(
                    entry -> {
                        var doc = documents.get(entry.getKey().getNamespaceURI());
                        var declaration = entry.getValue().declaration.create(doc, prefixes);
                        declaration.setAttribute("name", entry.getKey().getLocalPart());
                        doc.getDocumentElement().appendChild(declaration);
                    });
            var serialized = new HashMap<String, String>();
            var transformers = TransformerFactory.newInstance();
            transformers.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            transformers.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            for (var entry : documents.entrySet()) {
                var output = new StringWriter();
                transformers
                    .newTransformer()
                    .transform(new DOMSource(entry.getValue()), new StreamResult(output));
                serialized.put(locations.get(entry.getKey()), output.toString());
            }
            var schemas = SchemaFactory.newInstance(XS);
            schemas.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            schemas.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            schemas.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var implementation = (DOMImplementationLS) base.getImplementation().getFeature("LS",
                "3.0");
            schemas.setResourceResolver(
                (type, namespace, publicId, systemId, baseURI) -> {
                    var source = serialized.get(systemId);
                    if (source == null)
                        throw new IllegalArgumentException(
                            "External screen schema is not registered: " + systemId);
                    var input = implementation.createLSInput();
                    input.setSystemId(systemId);
                    input.setStringData(source);
                    return input;
                });
            var location = locations.get(ScreenReader.NAMESPACE);
            return schemas.newSchema(
                new StreamSource(new StringReader(serialized.get(location)), location));
        } catch (Exception failure) {
            throw new IllegalStateException(
                "Invalid registered screen XML schema: " + failure.getMessage(), failure);
        }
    }
}
