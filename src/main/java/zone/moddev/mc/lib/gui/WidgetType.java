package zone.moddev.mc.lib.gui;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import zone.moddev.mc.lib.gui.model.elements.CustomElement;
import zone.moddev.mc.lib.gui.value.ValueType;

/**
 * A mod-defined leaf widget's common-side contract. Register it during mod
 * construction, before screens load. The validator runs on both sides at
 * startup and must not access client classes or a live world. Reject
 * unsupported properties and missing bindings with IllegalArgumentException. An
 * empty valueKinds set forbids a value binding; max bindings are always
 * numeric. Only the typed actions listed here may be invoked by this widget.
 * Their handlers run on the server.
 */
public record WidgetType(Set<ValueType<?>> valueKinds,
    Consumer<CustomElement> validator,
    java.util.List<WidgetAction<?>> actions) {
    public WidgetType(Set<ValueType<?>> valueKinds,
        Consumer<CustomElement> validator) {
        this(valueKinds, validator, java.util.List.of());
    }

    public WidgetType {
        valueKinds = Set.copyOf(valueKinds);
        Objects.requireNonNull(validator, "validator");
        actions = java.util.List.copyOf(actions);
        var ids = new java.util.HashSet<net.minecraft.resources.ResourceLocation>();
        for (var action : actions)
            if (!ids.add(action.id()))
                throw new IllegalArgumentException(
                    "Duplicate widget action: " + action.id());
    }

    public java.util.Optional<WidgetAction<?>> action(
        net.minecraft.resources.ResourceLocation id) {
        return actions.stream().filter(action -> action.id().equals(id))
            .findFirst();
    }
}
