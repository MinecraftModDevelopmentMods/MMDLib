package zone.moddev.mc.lib.gui.load;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.validation.Schema;

/**
 * Common-side XML tag registry. Register on both sides during mod construction.
 */
public final class ScreenTags {
    public static final ScreenTags GLOBAL = builtins();
    private final Map<QName, ScreenTag<?>> tags = new LinkedHashMap<>();
    private Schema schema;
    private boolean frozen;

    public static ScreenTags builtins() {
        var registry = new ScreenTags();
        BuiltinElements.register(registry);
        return registry;
    }

    public static QName name(String local) {
        return new QName(ScreenReader.NAMESPACE, ScreenSchemas.name(local));
    }

    public synchronized void register(String local, ScreenTag<?> tag) {
        register(name(local), tag);
    }

    public synchronized void register(QName name, ScreenTag<?> tag) {
        writable();
        checkName(name);
        if (tags.putIfAbsent(name, Objects.requireNonNull(tag)) != null)
            throw new IllegalArgumentException("Duplicate screen tag: " + name);
        schema = null;
    }

    public synchronized void replace(QName name, ScreenTag<?> tag) {
        writable();
        checkName(name);
        if (!tags.containsKey(name))
            throw new IllegalArgumentException("Unknown screen tag: " + name);
        tags.put(name, Objects.requireNonNull(tag));
        schema = null;
    }

    private static void checkName(QName name) {
        ScreenSchemas.name(name.getLocalPart());
        if (name.getNamespaceURI().isEmpty())
            throw new IllegalArgumentException(
                "Screen tags require an XML namespace");
        if (Set
            .of(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI,
                javax.xml.XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI,
                javax.xml.XMLConstants.XML_NS_URI,
                javax.xml.XMLConstants.XMLNS_ATTRIBUTE_NS_URI)
            .contains(name.getNamespaceURI()))
            throw new IllegalArgumentException(
                "Reserved XML namespace: " + name.getNamespaceURI());
    }

    private void writable() {
        if (frozen)
            throw new IllegalStateException(
                "Screen tags are frozen; register during mod construction");
    }

    public synchronized ScreenTag<?> require(QName name) {
        var tag = tags.get(name);
        if (tag == null)
            throw new IllegalArgumentException("Unknown screen tag: " + name);
        return tag;
    }

    public ScreenTag<?> require(ScreenReader.Node node) {
        try {
            return require(node.tag());
        } catch (IllegalArgumentException failure) {
            throw new ScreenException(node.source(), node.line(),
                failure.getMessage(), failure);
        }
    }

    public boolean is(ScreenReader.Node node, ScreenTag.Role role) {
        return require(node).role() == role;
    }

    public synchronized void freeze() {
        schema();
        frozen = true;
    }

    synchronized Schema schema() {
        if (schema == null)
            schema = ScreenSchemas.compile(Map.copyOf(tags));
        return schema;
    }
}
