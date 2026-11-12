package zone.moddev.mc.lib.gui;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import javax.xml.namespace.QName;

import zone.moddev.mc.lib.gui.load.ScreenAttribute;
import zone.moddev.mc.lib.gui.load.ScreenTag;
import zone.moddev.mc.lib.gui.load.ScreenTags;
import zone.moddev.mc.lib.gui.load.ScreenXml;
import zone.moddev.mc.lib.gui.model.Element;

/**
 * Common-side element declaration, not a registry. XML is optional; Java-only
 * types may also be abstract classes or interfaces used for inherited client
 * registrations. Contains no client types.
 */
public final class ElementType<E extends Element> {
    private final Class<E> type;
    private final Map<QName, ScreenTag<? extends E>> tags;

    private ElementType(Class<E> type,
        Map<QName, ScreenTag<? extends E>> tags) {
        this.type = Objects.requireNonNull(type);
        this.tags = Collections.unmodifiableMap(new LinkedHashMap<>(tags));
    }

    public static <E extends Element> ElementType<E> of(Class<E> type) {
        return new ElementType<>(type, Map.of());
    }

    public static <E extends Element> ElementType<E> xml(QName name,
        ScreenTag<E> tag) {
        return of(tag.resultType()).withXml(name, tag);
    }

    /**
     * Build a namespaced element tag using the existing schema/attribute
     * contract.
     */
    public static <E extends Element> Builder<E> xml(Class<E> type, QName name,
        Function<ScreenXml, E> factory) {
        return new Builder<>(name, ScreenTag.element(type, factory));
    }

    /**
     * Returns a new declaration with another XML spelling; the original remains
     * unchanged.
     */
    public ElementType<E> withXml(QName name, ScreenTag<? extends E> tag) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(tag);
        if (tag.role() != ScreenTag.Role.ELEMENT
            || !type.isAssignableFrom(tag.resultType()))
            throw new IllegalArgumentException(
                "Not an element tag for " + type.getName() + ": " + name);
        var copy = new LinkedHashMap<>(tags);
        if (copy.putIfAbsent(name, tag) != null)
            throw new IllegalArgumentException(
                "Duplicate element tag: " + name);
        return new ElementType<>(type, copy);
    }

    public Class<E> type() {
        return type;
    }

    /** Register during mod construction, on both physical sides. */
    public void register() {
        register(ScreenTags.GLOBAL);
    }

    /**
     * Isolated loaders use the same declaration without modifying global
     * registrations.
     */
    public void register(ScreenTags registry) {
        Objects.requireNonNull(registry);
        tags.forEach(registry::register);
    }

    /** Fluent convenience for the existing ScreenTag builder. */
    public static final class Builder<E extends Element> {
        private final QName name;
        private final ScreenTag.Builder<E> tag;

        private Builder(QName name, ScreenTag.Builder<E> tag) {
            this.name = Objects.requireNonNull(name);
            this.tag = tag;
        }

        public Builder<E> attribute(String name, ScreenAttribute attribute) {
            tag.attribute(name, attribute);
            return this;
        }

        public Builder<E> groups(String... groups) {
            tag.groups(groups);
            return this;
        }

        public Builder<E> children(String group, int minimum, int maximum) {
            tag.children(group, minimum, maximum);
            return this;
        }

        public Builder<E> children(QName child, int minimum, int maximum) {
            tag.children(child, minimum, maximum);
            return this;
        }

        public ElementType<E> build() {
            return ElementType.xml(name, tag.build());
        }
    }
}
