package zone.moddev.mc.lib.gui.model.elements;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.ElementAction;
import zone.moddev.mc.lib.gui.ElementActions;
import zone.moddev.mc.lib.gui.WidgetTypes;
import zone.moddev.mc.lib.gui.layout.TextMeasure;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.value.ValueType;

/**
 * A mod-defined leaf. Properties are immutable and sorted so screen
 * fingerprints are stable across JVMs and XML property order. Property nodes
 * are configuration, not child elements.
 */
public class CustomElement implements Element {
    private final Common common;
    private final ResourceLocation type;
    private final int width;
    private final int height;
    private final Map<String, String> properties;

    public CustomElement(Common common, ResourceLocation type, int width,
        int height, Map<String, String> properties) {
        Objects.requireNonNull(common, "common");
        Objects.requireNonNull(type, "type");
        if (width < 1 || width > 1024 || height < 1 || height > 1024)
            throw new IllegalArgumentException(
                "Custom widget dimensions must be from 1 to 1024 px");
        properties = java.util.Collections
            .unmodifiableSortedMap(new java.util.TreeMap<>(properties));

        this.common = common;
        this.type = type;
        this.width = width;
        this.height = height;
        this.properties = properties;
    }

    /**
     * Escape property values so punctuation cannot make different
     * configurations fingerprint alike.
     */
    @Override
    public String toString() {
        var json = new com.google.gson.JsonObject();
        properties.forEach(json::addProperty);
        return "CustomElement[common=" + common + ", type=" + type + ", width="
            + width + ", height=" + height + ", properties=" + json + "]";
    }

    public int[] measure(TextMeasure text) {
        return new int[]{width, height};
    }

    public Set<ValueType<?>> valueKinds(WidgetTypes widgets) {
        return widgets.require(type).valueKinds();
    }

    public String validationName() {
        return type.toString();
    }

    public void validate(WidgetTypes widgets) {
        validate();
        var contract = widgets.require(type);
        if (common.value().isPresent() && contract.valueKinds().isEmpty())
            throw new IllegalArgumentException(
                "This widget does not accept a value binding");
        contract.validator().accept(this);
    }

    public List<? extends ElementAction<?>> actions() {
        var actions = new java.util.ArrayList<ElementAction<?>>();
        actions.add(ElementActions.CLICK);
        actions.addAll(WidgetTypes.GLOBAL.require(type).actions());
        return List.copyOf(actions);
    }

    public Common common() {
        return common;
    }

    public ResourceLocation type() {
        return type;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public Map<String, String> properties() {
        return properties;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (object == null || getClass() != object.getClass())
            return false;
        var other = (CustomElement) object;
        return java.util.Objects.equals(common, other.common)
            && java.util.Objects.equals(type, other.type)
            && java.util.Objects.equals(width, other.width)
            && java.util.Objects.equals(height, other.height)
            && java.util.Objects.equals(properties, other.properties);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(common);
        hash = 31 * hash + java.util.Objects.hashCode(type);
        hash = 31 * hash + java.util.Objects.hashCode(width);
        hash = 31 * hash + java.util.Objects.hashCode(height);
        hash = 31 * hash + java.util.Objects.hashCode(properties);
        return hash;
    }
}
