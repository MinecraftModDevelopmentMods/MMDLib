package zone.moddev.mc.lib.machine.feature;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;

import zone.moddev.mc.lib.api.util.ChangeSignal;
import zone.moddev.mc.lib.machine.presentation.VisualVariableProvider;
import zone.moddev.mc.lib.machine.presentation.VisualVariables;

/**
 * Stored typed values. Mutations publish once at the end of a host operation.
 */
public final class ProcessorState implements VisualVariableProvider {
    public record Key<T>(String name, Codec<T> codec, T initial,
        UnaryOperator<T> copy, boolean visual, boolean synchronize) {
        public T initial() {
            return copy.apply(initial);
        }
    }

    private final Map<String, Key<?>> keys = new LinkedHashMap<>();
    private final Map<Key<?>, Object> values = new LinkedHashMap<>();
    private final Map<Key<?>, ChangeSignal> signals = new LinkedHashMap<>();
    private final Set<Key<?>> changed = new LinkedHashSet<>();
    private Runnable onChange = () -> {
    };
    private java.util.function.Consumer<Boolean> onBatch = ignored -> {
    };
    private int depth;

    public <T> Key<T> define(String name, Codec<T> codec, T initial,
        UnaryOperator<T> copy, boolean visual, boolean synchronize) {
        if (!name.matches("[a-zA-Z_][a-zA-Z0-9_]*") || keys.containsKey(name))
            throw new IllegalArgumentException(
                "Invalid or duplicate variable: " + name);
        if (visual)
            VisualVariables.Kind.of(initial);
        codec.encodeStart(NbtOps.INSTANCE, initial).getOrThrow();
        var key = new Key<>(name, codec, copy.apply(initial), copy, visual,
            synchronize);
        keys.put(name, key);
        values.put(key, copy.apply(initial));
        signals.put(key, new ChangeSignal());
        return key;
    }

    public <T> Key<T> define(String name, Codec<T> codec, T initial) {
        return define(name, codec, initial, UnaryOperator.identity(), true,
            true);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Key<T> key) {
        require(key);
        return key.copy().apply((T) values.get(key));
    }

    public <T> void set(Key<T> key, T value) {
        require(key);
        if (depth == 0)
            throw new IllegalStateException(
                "State mutation requires a host operation");
        var copy = key.copy().apply(Objects.requireNonNull(value));
        key.codec().encodeStart(NbtOps.INSTANCE, copy).getOrThrow();
        if (!Objects.equals(values.get(key), copy)) {
            values.put(key, copy);
            changed.add(key);
        }
    }

    private void require(Key<?> key) {
        if (keys.get(key.name()) != key)
            throw new IllegalArgumentException("Foreign variable key");
    }

    public void onChange(Runnable callback) {
        onChange = Objects.requireNonNull(callback);
    }
    public void onBatch(java.util.function.Consumer<Boolean> callback) {
        onBatch = Objects.requireNonNull(callback);
    }

    public void update(Runnable operation) {
        depth++;
        try {
            operation.run();
        } finally {
            if (--depth == 0 && !changed.isEmpty()) {
                var batch = Set.copyOf(changed);
                changed.clear();
                batch.forEach(key -> signals.get(key).changed());
                onChange.run();
                onBatch.accept(batch.stream().anyMatch(Key::synchronize));
            }
        }
    }

    public CompoundTag save(boolean client) {
        var tag = new CompoundTag();
        for (var key : keys.values())
            if (!client || key.synchronize())
                save(tag, key);
        return tag;
    }

    public Map<String, Object> clientDefaults() {
        var defaults = new LinkedHashMap<String, Object>();
        keys.values().stream().filter(Key::synchronize)
            .forEach(key -> defaults.put(key.name(), key.initial()));
        return Map.copyOf(defaults);
    }

    public Object clientValue(String name) {
        var key = keys.get(name);
        if (key == null || !key.synchronize())
            throw new IllegalArgumentException(
                "Unknown client-visible processor variable: " + name);
        return get(key);
    }

    private <T> void save(CompoundTag tag, Key<T> key) {
        tag.put(key.name(),
            key.codec().encodeStart(NbtOps.INSTANCE, get(key)).getOrThrow());
    }

    public void load(CompoundTag tag, boolean client) {
        // Decode everything before mutating, so malformed data cannot partially
        // restore state.
        var decoded = new LinkedHashMap<Key<?>, Object>();
        for (var key : keys.values())
            if (!client || key.synchronize())
                decoded.put(key, tag.contains(key.name())
                    ? key.codec().parse(NbtOps.INSTANCE, tag.get(key.name()))
                        .getOrThrow()
                    : key.initial());
        update(() -> decoded.forEach((key, value) -> restore(key, value)));
    }

    @SuppressWarnings("unchecked")
    private <T> void restore(Key<T> key, Object value) {
        set(key, (T) value);
    }

    public void defineVisualVariables(VisualVariables.Builder builder) {
        for (var key : keys.values())
            if (key.visual())
                builder.observedBy(signals.get(key)).add(key.name(),
                    key.initial(), () -> values.get(key));
    }
}
