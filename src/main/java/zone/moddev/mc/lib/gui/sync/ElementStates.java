package zone.moddev.mc.lib.gui.sync;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

import zone.moddev.mc.lib.gui.model.Element;

/**
 * Per-menu typed state. Transport owns addresses; definitions own codecs, sampling and validation.
 */
public final class ElementStates implements ElementStateView {
    public static final int FIELDS_PER_ELEMENT = 16, MAX_FIELDS = 4096 * FIELDS_PER_ELEMENT;
    public static final int MAX_TOTAL_BYTES = 1 << 18;
    private final Map<Element, Map<ElementSync<?>, Field<?>>> elements = new IdentityHashMap<>();
    private final Map<Integer, Field<?>> fields = new LinkedHashMap<>();
    private final RegistryAccess registries;
    private final Runnable layoutChanged;

    private final class Field<T> {
        final Element element;
        final ElementSync<T> definition;
        final int address;
        final ElementStateValue initial;
        ElementStateValue value;

        Field(Element element, ElementSync<T> definition, int address) {
            this.element = element;
            this.definition = definition;
            this.address = address;
            initial = definition.encode(registries, element, definition.initial(element));
            value = initial;
        }

        T read() {
            return definition.decode(registries, element, value);
        }

        ElementStateValue sample(BlockEntity machine, Player viewer) {
            return definition.encode(
                registries,
                element,
                definition.sample(new ElementSync.Source(element, machine, viewer), read()));
        }

        void validate(ElementStateValue encoded) {
            definition.decode(registries, element, encoded);
        }
    }

    public ElementStates(List<Element> order, RegistryAccess registries, Runnable layoutChanged) {
        if (order.size() > 4096)
            throw new IllegalArgumentException("Too many screen elements");
        this.registries = Objects.requireNonNull(registries);
        this.layoutChanged = Objects.requireNonNull(layoutChanged);
        for (int index = 0; index < order.size(); index++) {
            var element = order.get(index);
            if (!(element instanceof SynchronizedElement synchronizedElement))
                continue;
            var declarations = List.copyOf(synchronizedElement.synchronizedValues());
            if (declarations.size() > FIELDS_PER_ELEMENT)
                throw new IllegalArgumentException("Too many state fields for " + element);
            var byIdentity = new IdentityHashMap<ElementSync<?>, Field<?>>();
            var names = new HashSet<net.minecraft.resources.ResourceLocation>();
            for (int slot = 0; slot < declarations.size(); slot++) {
                var declaration = declarations.get(slot);
                if (!names.add(declaration.id()))
                    throw new IllegalArgumentException(
                        "Duplicate element state: " + declaration.id());
                var field = new Field<>(
                    element, declaration, Integer.MIN_VALUE + index * FIELDS_PER_ELEMENT + slot);
                byIdentity.put(declaration, field);
                fields.put(field.address, field);
            }
            elements.put(element, byIdentity);
        }
        checkBudget(snapshot());
    }

    @SuppressWarnings("unchecked")
    private <T> Field<T> field(Element element, ElementSync<T> definition) {
        var field = elements.getOrDefault(element, Map.of()).get(definition);
        if (field == null)
            throw new IllegalArgumentException("Undeclared element state: " + definition.id());
        return (Field<T>) field;
    }

    public <T> T get(Element element, ElementSync<T> definition) {
        return field(element, definition).read();
    }

    /** Trusted local mutation only. Client changes never grant server authority. */
    public <T> boolean set(Element element, ElementSync<T> definition, T value) {
        var field = field(element, definition);
        var encoded = definition.encode(registries, element, value);
        if (encoded.equals(field.value))
            return false;
        var updated = new LinkedHashMap<>(snapshot());
        updated.put(field.address, encoded);
        checkBudget(updated);
        field.value = encoded;
        if (definition.affectsLayout())
            layoutChanged.run();
        return true;
    }

    public Map<Integer, ElementStateValue> snapshot() {
        var result = new LinkedHashMap<Integer, ElementStateValue>();
        fields.forEach((address, field) -> result.put(address, field.value));
        return Map.copyOf(result);
    }

    /**
     * Sampling is per viewer. Ordinary value/max getters retain their shared machine tick cache.
     */
    public Map<Integer, ElementStateValue> sample(BlockEntity machine, Player viewer) {
        if (machine.getLevel() == null || machine.getLevel().isClientSide)
            throw new IllegalStateException("Element state may only be sampled on the server");
        var sampled = new LinkedHashMap<Integer, ElementStateValue>();
        fields.forEach((address, field) -> sampled.put(address, field.sample(machine, viewer)));
        apply(sampled);
        return Map.copyOf(sampled);
    }

    /** Validate the complete batch before changing values or notifying layout. */
    public void apply(Map<Integer, ?> delta) {
        var accepted = new LinkedHashMap<Integer, ElementStateValue>();
        delta.forEach(
            (address, value) -> {
                var field = fields.get(address);
                if (field == null)
                    return;
                if (!(value instanceof ElementStateValue encoded))
                    throw new IllegalArgumentException("Invalid element state envelope");
                field.validate(encoded);
                accepted.put(address, encoded);
            });
        var combined = new LinkedHashMap<>(snapshot());
        combined.putAll(accepted);
        checkBudget(combined);
        boolean layout = false;
        for (var entry : accepted.entrySet()) {
            var field = fields.get(entry.getKey());
            if (!entry.getValue().equals(field.value)) {
                field.value = entry.getValue();
                layout |= field.definition.affectsLayout();
            }
        }
        if (layout)
            layoutChanged.run();
    }

    /** Last confirmed values for timeout recovery; missing fields use their initial snapshots. */
    public Map<Integer, ElementStateValue> sent(Map<Integer, Object> values) {
        var result = new LinkedHashMap<Integer, ElementStateValue>();
        fields.forEach(
            (address, field) -> result.put(
                address,
                values.get(address) instanceof ElementStateValue value ? value : field.initial));
        return Map.copyOf(result);
    }

    public static void checkBudget(Map<Integer, ElementStateValue> values) {
        if (values.size() > MAX_FIELDS)
            throw new IllegalArgumentException("Too many element state values");
        long size = 0;
        for (var value : values.values())
            size += value.size() + 8L;
        if (size > MAX_TOTAL_BYTES)
            throw new IllegalArgumentException("Element states exceed the screen byte budget");
    }
}
