package zone.moddev.mc.lib.gui.load;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import javax.xml.namespace.QName;

import org.w3c.dom.Document;

import zone.moddev.mc.lib.gui.model.Element;

/**
 * A registered XML tag's factory, schema and placement groups. Immutable after
 * construction.
 */
public final class ScreenTag<T> {
    public enum Role {
        ELEMENT, DATA, DOCUMENT, FILL, PLACEHOLDER
    }

    @FunctionalInterface
    interface Declaration {
        org.w3c.dom.Element create(Document document,
            Map<String, String> prefixes);
    }

    final Declaration declaration;
    private final Class<T> resultType;
    private final Function<ScreenXml, T> factory;
    private final Set<String> groups;
    private final Role role;

    ScreenTag(Class<T> resultType, Function<ScreenXml, T> factory,
        Set<String> groups, Role role, Declaration declaration) {
        this.resultType = Objects.requireNonNull(resultType);
        this.factory = Objects.requireNonNull(factory);
        this.groups = Set.copyOf(groups);
        this.role = Objects.requireNonNull(role);
        this.declaration = declaration;
    }

    public Class<T> resultType() {
        return resultType;
    }

    public Set<String> groups() {
        return groups;
    }

    public Role role() {
        return role;
    }

    T create(ScreenXml xml) {
        return resultType.cast(Objects.requireNonNull(factory.apply(xml),
            "Tag factory returned null"));
    }

    public static <E extends Element> Builder<E> element(Class<E> type,
        Function<ScreenXml, E> factory) {
        return new Builder<>(type, factory).common().group("widget")
            .role(Role.ELEMENT);
    }

    /**
     * Data children or document roots can return values other than model
     * elements.
     */
    public static <T> Builder<T> data(Class<T> type,
        Function<ScreenXml, T> factory) {
        return new Builder<>(type, factory);
    }

    public static final class Builder<T> {
        private final Class<T> type;
        private final Function<ScreenXml, T> factory;
        private final Map<String, ScreenAttribute> attributes = new LinkedHashMap<>();
        private final Set<String> groups = new LinkedHashSet<>();
        private Role role = Role.DATA;
        private boolean common;
        private String childGroup;
        private QName childTag;
        private int minimum, maximum;

        private Builder(Class<T> type, Function<ScreenXml, T> factory) {
            this.type = type;
            this.factory = factory;
        }

        public Builder<T> common() {
            common = true;
            return this;
        }

        /**
         * Group membership controls where a tag is legal; built-ins use widget,
         * slots, buttons, views, radios and dock.
         */
        public Builder<T> group(String name) {
            groups.add(ScreenSchemas.name(name));
            return this;
        }

        /**
         * Replace default memberships, for tags restricted to a particular
         * container or dock.
         */
        public Builder<T> groups(String... names) {
            groups.clear();
            for (var name : names)
                group(name);
            return this;
        }

        public Builder<T> role(Role role) {
            this.role = role;
            return this;
        }

        public Builder<T> attribute(String name, ScreenAttribute attribute) {
            if (attributes.putIfAbsent(ScreenSchemas.name(name),
                Objects.requireNonNull(attribute)) != null)
                throw new IllegalArgumentException(
                    "Duplicate attribute: " + name);
            return this;
        }

        /** Accept a registered group of children; -1 means no upper bound. */
        public Builder<T> children(String group, int minimum, int maximum) {
            bounds(minimum, maximum);
            childGroup = ScreenSchemas.name(group);
            childTag = null;
            return this;
        }

        /**
         * Accept one exact registered child tag, including a mod-owned data
         * tag.
         */
        public Builder<T> children(QName tag, int minimum, int maximum) {
            bounds(minimum, maximum);
            childTag = Objects.requireNonNull(tag);
            childGroup = null;
            return this;
        }

        private void bounds(int min, int max) {
            if (min < 0 || (max != -1 && max < min))
                throw new IllegalArgumentException("Invalid child count");
            minimum = min;
            maximum = max;
        }

        public ScreenTag<T> build() {
            var attrs = Map.copyOf(attributes);
            var group = childGroup;
            var tag = childTag;
            int min = minimum, max = maximum;
            boolean shared = common;
            return new ScreenTag<>(type, factory, groups, role,
                (doc, prefixes) -> {
                    var element = ScreenSchemas.xs(doc, "element");
                    var complex = ScreenSchemas.xs(doc, "complexType");
                    element.appendChild(complex);
                    if (group != null || tag != null) {
                        var sequence = ScreenSchemas.xs(doc, "sequence");
                        complex.appendChild(sequence);
                        var child = ScreenSchemas.xs(doc,
                            group != null ? "group" : "element");
                        child.setAttribute("ref",
                            group != null
                                ? "ui:" + group
                                : ScreenSchemas.reference(tag, prefixes));
                        child.setAttribute("minOccurs", Integer.toString(min));
                        child.setAttribute("maxOccurs",
                            max == -1 ? "unbounded" : Integer.toString(max));
                        sequence.appendChild(child);
                    }
                    if (shared) {
                        var ref = ScreenSchemas.xs(doc, "attributeGroup");
                        ref.setAttribute("ref", "ui:common");
                        complex.appendChild(ref);
                    }
                    attrs.entrySet().stream().sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> {
                            var attribute = ScreenSchemas.xs(doc, "attribute");
                            attribute.setAttribute("name", entry.getKey());
                            var spec = entry.getValue();
                            if (spec.required())
                                attribute.setAttribute("use", "required");
                            if (spec.defaultValue() != null)
                                attribute.setAttribute("default",
                                    spec.defaultValue());
                            if (spec.choices().isEmpty())
                                attribute.setAttribute("type", spec.type());
                            else {
                                var simple = ScreenSchemas.xs(doc,
                                    "simpleType");
                                var restriction = ScreenSchemas.xs(doc,
                                    "restriction");
                                restriction.setAttribute("base", spec.type());
                                simple.appendChild(restriction);
                                attribute.appendChild(simple);
                                for (var choice : spec.choices()) {
                                    var value = ScreenSchemas.xs(doc,
                                        "enumeration");
                                    value.setAttribute("value", choice);
                                    restriction.appendChild(value);
                                }
                            }
                            complex.appendChild(attribute);
                        });
                    return element;
                });
        }
    }
}
