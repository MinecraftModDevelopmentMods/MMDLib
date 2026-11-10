package zone.moddev.mc.lib.gui.runtime.component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.core.RegistryAccess;

import zone.moddev.mc.lib.gui.sync.ElementStateValue;
import zone.moddev.mc.lib.gui.sync.ElementStates;

/**
 * Per-menu encoded storage with registry-assigned addresses and atomic, bounded
 * updates.
 */
public final class ComponentStates {
    public static final int MAX_FIELDS = 4096;
    private static final int BASE = Integer.MIN_VALUE
        + ElementStates.MAX_FIELDS;

    public record Declaration(MenuComponentKey<?> component,
        ComponentSync<?> field) {
    }

    private final RegistryAccess registries;
    private final Map<MenuComponent, Map<ComponentSync<?>, Field<?>>> components = new IdentityHashMap<>();
    private final Map<Integer, Field<?>> fields = new LinkedHashMap<>();

    private final class Field<T> {
        final MenuComponent owner;
        final ComponentSync<T> definition;
        final int address;
        ElementStateValue value;

        Field(MenuComponent owner, ComponentSync<T> definition, int address) {
            this.owner = owner;
            this.definition = definition;
            this.address = address;
            value = definition.encode(registries, definition.initial().get());
        }

        T read() {
            return definition.decode(registries, value);
        }
    }

    public ComponentStates(List<Declaration> schema,
        Map<MenuComponentKey<?>, MenuComponent> active,
        RegistryAccess registries) {
        this.registries = Objects.requireNonNull(registries);
        if (schema.size() > MAX_FIELDS)
            throw new IllegalArgumentException("Too many component fields");
        var names = new HashSet<String>();
        for (int index = 0; index < schema.size(); index++) {
            var declaration = schema.get(index);
            if (!names.add(
                declaration.component().id() + "/" + declaration.field().id()))
                throw new IllegalArgumentException(
                    "Duplicate component field: " + declaration.field().id());
            var owner = active.get(declaration.component());
            if (owner == null)
                continue;
            var field = new Field<>(owner, declaration.field(), BASE + index);
            fields.put(field.address, field);
            components.computeIfAbsent(owner, key -> new IdentityHashMap<>())
                .put(declaration.field(), field);
        }
        ElementStates.checkBudget(snapshot());
    }

    @SuppressWarnings("unchecked")
    private <T> Field<T> field(MenuComponent owner,
        ComponentSync<T> definition) {
        var field = components.getOrDefault(owner, Map.of()).get(definition);
        if (field == null)
            throw new IllegalArgumentException(
                "Undeclared component field: " + definition.id());
        return (Field<T>) field;
    }

    public <T> T get(MenuComponent owner, ComponentSync<T> field) {
        return field(owner, field).read();
    }

    public Map<Integer, ElementStateValue> snapshot() {
        var result = new LinkedHashMap<Integer, ElementStateValue>();
        fields.forEach((address, field) -> result.put(address, field.value));
        return Map.copyOf(result);
    }

    public Map<Integer, Object> sample(Collection<MenuComponent> sources) {
        var sampled = new LinkedHashMap<Integer, Object>();
        for (var source : sources)
            if (source instanceof MenuSnapshot sampler)
                sampler.snapshot(new ComponentSnapshot() {
                    public <T> void put(ComponentSync<T> definition, T value) {
                        var field = field(source, definition);
                        if (sampled.putIfAbsent(field.address,
                            definition.encode(registries, value)) != null)
                            throw new IllegalArgumentException(
                                "Component sampled a field twice: "
                                    + definition.id());
                    }
                });
        apply(sampled);
        return new LinkedHashMap<>(snapshot());
    }

    public void apply(Map<Integer, ?> delta) {
        var accepted = new LinkedHashMap<Integer, ElementStateValue>();
        delta.forEach((address, value) -> {
            if (address < BASE || address >= BASE + MAX_FIELDS)
                return;
            var field = fields.get(address);
            if (field == null)
                throw new IllegalArgumentException(
                    "Unknown component field address");
            if (!(value instanceof ElementStateValue encoded))
                throw new IllegalArgumentException(
                    "Invalid component state envelope");
            field.definition.decode(registries, encoded);
            accepted.put(address, encoded);
        });
        var combined = new LinkedHashMap<>(snapshot());
        combined.putAll(accepted);
        ElementStates.checkBudget(combined);
        var changed = Collections
            .newSetFromMap(new IdentityHashMap<MenuComponent, Boolean>());
        accepted.forEach((address, value) -> {
            var field = fields.get(address);
            if (!value.equals(field.value)) {
                field.value = value;
                changed.add(field.owner);
            }
        });
        for (var component : changed)
            if (component instanceof MenuSnapshot target)
                target.applied(new ComponentStateView() {
                    public <T> T get(ComponentSync<T> field) {
                        return ComponentStates.this.get(component, field);
                    }
                });
    }
}
