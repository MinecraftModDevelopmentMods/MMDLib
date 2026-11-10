package zone.moddev.mc.lib.gui.load;

import java.io.IOException;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.validation.Schema;

import net.minecraft.resources.ResourceLocation;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import zone.moddev.mc.lib.gui.WidgetTypes;
import zone.moddev.mc.lib.gui.model.Screen;

/**
 * Reads a screen file into a {@link Screen}. Registered tag contracts validate
 * structure, attribute names and value formats. Model validation then checks
 * unique IDs and slot indexes, references between elements, required pairings,
 * and each element's needs. Errors name the file and line.
 *
 * <p>
 * The parser is hardened: DOCTYPE declarations, external entities and XInclude
 * are refused, so a screen file cannot read other files or expand entities.
 */
public final class ScreenReader {
    /** The XML namespace of screen files. */
    public static final String NAMESPACE = "https://moddev.zone/mmdlib/screen";

    private ScreenReader() {
    }

    /**
     * Reads one screen file.
     *
     * @param id
     *            the screen ID, from the file's path
     * @param source
     *            names the file in error messages
     * @throws ScreenException
     *             if the file is not well-formed, breaks the schema, or fails a
     *             check
     * @throws IOException
     *             if the stream cannot be read
     */
    public static Screen read(ResourceLocation id, String source,
        InputStream input) throws IOException {
        return read(id, source, input, WidgetTypes.GLOBAL);
    }

    /**
     * Reads using an explicit widget registry, for tools that own their loading
     * stage.
     */
    public static Screen read(ResourceLocation id, String source,
        InputStream input, WidgetTypes widgets) throws IOException {
        return read(id, source, input, widgets, ScreenTags.GLOBAL);
    }

    /** Reads with an isolated XML registry, including namespaced mod tags. */
    public static Screen read(ResourceLocation id, String source,
        InputStream input, WidgetTypes widgets, ScreenTags tags)
        throws IOException {
        Node root = parse(source, input, tags);
        return build(id,
            new ScreenTemplates(Map.of(id, root),
                ScreenFillGenerator.UNAVAILABLE, tags).expand(id),
            widgets, tags);
    }

    static Screen build(ResourceLocation id, Node root, WidgetTypes widgets) {
        return build(id, root, widgets, ScreenTags.GLOBAL);
    }

    static Screen build(ResourceLocation id, Node root, WidgetTypes widgets,
        ScreenTags tags) {
        root = validateExpanded(root, tags);
        var screen = new ScreenXml(id, root, widgets, tags).read(root,
            Screen.class);
        ScreenValidation.check(screen, widgets);
        return screen;
    }

    // Parsing

    /**
     * One parsed element, with its attributes as written (schema defaults
     * filled in).
     */
    public record Node(String name, Map<String, String> attributes,
        List<Node> children, String source, int line, String namespace) {
        public Node(String name, Map<String, String> attributes,
            List<Node> children, String source, int line) {
            this(name, attributes, children, source, line, NAMESPACE);
        }

        public QName tag() {
            return new QName(namespace, name);
        }

        public Node withChildren(List<Node> children) {
            return new Node(name, attributes, children, source, line,
                namespace);
        }
    }

    static Node parse(String source, InputStream input) throws IOException {
        return parse(source, input, ScreenTags.GLOBAL);
    }

    static Node parse(String source, InputStream input, ScreenTags tags)
        throws IOException {
        return parseTree(source, input, tags.schema());
    }

    /**
     * Secure, source-aware tree reader for machine definitions containing
     * embedded screens.
     */
    public static Node parseMachine(String source, InputStream input)
        throws IOException {
        return parseTree(source, input, null);
    }

    private static Node parseTree(String source, InputStream input,
        Schema schema) throws IOException {
        try {
            var factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(
                "http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities",
                false);
            factory.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false);
            if (schema != null)
                factory.setSchema(schema);
            var handler = new TreeHandler(source, schema == null);
            var parser = factory.newSAXParser();
            parser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            parser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            parser.parse(new InputSource(input), handler);
            return freeze(handler.root);
        } catch (SAXParseException ex) {
            throw new ScreenException(source, ex.getLineNumber(),
                ex.getMessage(), ex);
        } catch (SAXException | ParserConfigurationException ex) {
            throw new ScreenException(source, 0, ex.getMessage(), ex);
        }
    }

    private static Node freeze(Node node) {
        return new Node(node.name(), Map.copyOf(node.attributes()),
            node.children().stream().map(ScreenReader::freeze).toList(),
            node.source(), node.line(), node.namespace());
    }

    /**
     * Check placement again after fills are spliced into their destination
     * containers.
     */
    static Node validateExpanded(Node root, ScreenTags tags) {
        var validator = tags.schema().newValidatorHandler();
        var locator = new org.xml.sax.helpers.LocatorImpl();
        validator.setDocumentLocator(locator);
        var tree = new TreeHandler(root.source(), false);
        tree.setDocumentLocator(locator);
        validator.setContentHandler(tree);
        validator.setErrorHandler(new DefaultHandler() {
            @Override
            public void error(SAXParseException ex) throws SAXException {
                throw ex;
            }

            @Override
            public void fatalError(SAXParseException ex) throws SAXException {
                throw ex;
            }
        });
        try {
            validator.startDocument();
            validateNode(root, validator, locator);
            validator.endDocument();
            return freeze(tree.root);
        } catch (SAXException ex) {
            throw new ScreenException(locator.getSystemId(),
                locator.getLineNumber(), ex.getMessage(), ex);
        }
    }

    private static void validateNode(Node node,
        javax.xml.validation.ValidatorHandler validator,
        org.xml.sax.helpers.LocatorImpl locator) throws SAXException {
        locator.setSystemId(node.source());
        locator.setLineNumber(node.line());
        var attributes = new org.xml.sax.helpers.AttributesImpl();
        node.attributes().forEach((name, value) -> attributes.addAttribute("",
            name, name, "CDATA", value));
        // The URI, not the author's chosen prefix, is the registry identity.
        validator.startPrefixMapping("", node.namespace());
        validator.startElement(node.namespace(), node.name(), node.name(),
            attributes);
        for (var child : node.children())
            validateNode(child, validator, locator);
        locator.setSystemId(node.source());
        locator.setLineNumber(node.line());
        validator.endElement(node.namespace(), node.name(), node.name());
        validator.endPrefixMapping("");
    }

    private static final class TreeHandler extends DefaultHandler {
        private final String source;
        private final List<List<Node>> open = new ArrayList<>();
        private final List<Node> stack = new ArrayList<>();
        private Locator locator;
        private Node root;
        private final boolean machine;

        TreeHandler(String source, boolean machine) {
            this.source = source;
            this.machine = machine;
        }

        @Override
        public void setDocumentLocator(Locator locator) {
            this.locator = locator;
        }

        @Override
        public void startElement(String uri, String local, String qName,
            Attributes attributes) throws SAXException {
            if (uri.isEmpty())
                throw new SAXParseException(
                    "Screen elements require an XML namespace", locator);
            var values = new LinkedHashMap<String, String>();
            for (int i = 0; i < attributes.getLength(); i++)
                if (attributes.getURI(i).isEmpty())
                    values.put(attributes.getLocalName(i),
                        attributes.getValue(i));
            var children = new ArrayList<Node>();
            var node = new Node(local, values, children,
                locator.getSystemId() == null ? source : locator.getSystemId(),
                locator.getLineNumber(),
                machine && uri.equals("https://moddev.zone/mmdlib/machine")
                    ? NAMESPACE
                    : uri);
            if (stack.isEmpty())
                root = node;
            else
                open.getLast().add(node);
            stack.add(node);
            open.add(children);
        }

        @Override
        public void characters(char[] text, int start, int length)
            throws SAXException {
            if (!new String(text, start, length).isBlank())
                throw new SAXParseException(
                    "Inline text is not allowed; use text attributes", locator);
        }

        @Override
        public void endElement(String uri, String local, String qName) {
            stack.removeLast();
            open.removeLast();
        }

        @Override
        public void error(SAXParseException ex) throws SAXException {
            throw ex; // Schema violations are errors, not warnings.
        }

        @Override
        public void fatalError(SAXParseException ex) throws SAXException {
            throw ex;
        }
    }
}
