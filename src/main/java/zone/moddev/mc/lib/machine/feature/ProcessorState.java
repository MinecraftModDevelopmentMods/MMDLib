package zone.moddev.mc.lib.machine.feature;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
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
    private final Set<Key<?>> portable = new LinkedHashSet<>();
    private final Map<String, PortableVariable<?>> portableReferences = new LinkedHashMap<>();

    public java.util.Optional<PortableVariable<?>> portableVariable(
        String name) {
        return java.util.Optional.ofNullable(portableReferences.get(name));
    }
    private record Derived<T>(List<Key<?>> dependencies,
        Function<ProcessorState, T> compute) {
    }
    private final Map<Key<?>, Derived<?>> derived = new LinkedHashMap<>();
    private final Set<Key<?>> dirty = new LinkedHashSet<>();
    private Set<Key<?>> reading;
    private Runnable onChange = () -> {
    };
    private java.util.function.Consumer<Boolean> onBatch = ignored -> {
    };
    private int depth;
    private final List<ProcessorTimer> timers = new java.util.ArrayList<>();

    public ProcessorTimer timer(String name, int maximum,
        ProcessorTimer.Policy policy) {
        var timer = new ProcessorTimer(this, name, maximum, policy);
        timers.add(timer);
        return timer;
    }

    void advanceTimers(int deltaTicks, Processor.ProcessState phase) {
        timers.forEach(timer -> timer.advance(deltaTicks, phase));
    }

    public Declaration<Boolean> bool(String name, boolean initial) {
        return value(name, Codec.BOOL, initial, UnaryOperator.identity());
    }

    public Declaration<Integer> integer(String name, int minimum, int maximum,
        int initial) {
        return value(name, Codec.intRange(minimum, maximum), initial,
            UnaryOperator.identity());
    }

    public Declaration<String> string(String name, String initial) {
        return value(name, Codec.STRING, initial, UnaryOperator.identity());
    }

    public Declaration<zone.moddev.mc.lib.api.entity.EntitySnapshot> entity(
        String name) {
        return value(name, zone.moddev.mc.lib.api.entity.EntitySnapshot.CODEC,
            zone.moddev.mc.lib.api.entity.EntitySnapshot.EMPTY,
            UnaryOperator.identity());
    }

    public Declaration<List<net.minecraft.network.chat.Component>> components(
        String name, List<net.minecraft.network.chat.Component> initial) {
        return value(name,
            net.minecraft.network.chat.ComponentSerialization.CODEC.listOf(),
            initial,
            lines -> lines.stream().<net.minecraft.network.chat.Component>map(
                net.minecraft.network.chat.Component::copy).toList());
    }

    public <T> Declaration<T> value(String name, Codec<T> codec, T initial,
        UnaryOperator<T> copy) {
        return new Declaration<>(name, codec, initial, copy);
    }

    /**
     * Named options replace positional boolean flags; values are private unless
     * opted in.
     */
    public final class Declaration<T> {
        private final String name;
        private final Codec<T> codec;
        private final T initial;
        private final UnaryOperator<T> copy;
        private boolean visual, synchronize, portable;
        private PortableVariable<T> reference;

        Declaration<T> reference(PortableVariable<T> value) {
            reference = value;
            return this;
        }

        private Declaration(String name, Codec<T> codec, T initial,
            UnaryOperator<T> copy) {
            this.name = name;
            this.codec = codec;
            this.copy = copy;
            this.initial = copy.apply(initial);
        }

        public Declaration<T> synced() {
            synchronize = true;
            return this;
        }
        public Declaration<T> visual() {
            visual = true;
            synchronize = true;
            return this;
        }
        public Declaration<T> portable() {
            portable = true;
            return this;
        }

        public Key<T> define() {
            var key = ProcessorState.this.define(name, codec, initial, copy,
                visual, synchronize);
            if (reference != null)
                portableReferences.put(name, reference);
            return portable ? ProcessorState.this.portable(key) : key;
        }

        public Key<T> derived(List<Key<?>> dependencies,
            Function<ProcessorState, T> compute) {
            if (portable)
                throw new IllegalArgumentException(
                    "Derived values cannot be portable");
            return derive(name, codec, copy, visual, synchronize, dependencies,
                compute);
        }
    }

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

    /**
     * Dependencies must already exist, so the graph is acyclic by construction.
     */
    public <T> Key<T> derive(String name, Codec<T> codec, UnaryOperator<T> copy,
        boolean visual, boolean synchronize, List<Key<?>> dependencies,
        Function<ProcessorState, T> compute) {
        dependencies.forEach(this::require);
        var rule = new Derived<>(List.copyOf(dependencies),
            Objects.requireNonNull(compute));
        var key = define(name, codec, evaluate(rule), copy, visual,
            synchronize);
        derived.put(key, rule);
        return key;
    }

    private <T> T evaluate(Derived<T> rule) {
        var previous = reading;
        reading = Set.copyOf(rule.dependencies());
        try {
            return rule.compute().apply(this);
        } finally {
            reading = previous;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void refresh(Key<T> key) {
        if (dirty.contains(key)) {
            var value = evaluate((Derived<T>) derived.get(key));
            dirty.remove(key);
            assign(key, value);
        }
    }

    /**
     * Refresh context-dependent values once after the host receives its level.
     */
    public void refreshDerived() {
        update(() -> dirty.addAll(derived.keySet()));
    }

    /**
     * Opt a variable into item transport; execution state stays on the machine.
     */
    public <T> Key<T> portable(Key<T> key) {
        require(key);
        if (derived.containsKey(key))
            throw new IllegalArgumentException(
                "Derived variables cannot own portable data");
        portable.add(key);
        return key;
    }

    public CompoundTag savePortable() {
        var tag = new CompoundTag();
        portable.forEach(key -> save(tag, key));
        return tag;
    }

    public CompoundTag portableDefaults() {
        var tag = new CompoundTag();
        portable.forEach(key -> saveInitial(tag, key));
        return tag;
    }

    private <T> void saveInitial(CompoundTag tag, Key<T> key) {
        tag.put(key.name(), key.codec()
            .encodeStart(NbtOps.INSTANCE, key.initial()).getOrThrow());
    }

    public void loadPortable(CompoundTag tag) {
        var decoded = new LinkedHashMap<Key<?>, Object>();
        for (var key : portable)
            decoded.put(key, portableValue(tag, key));
        update(() -> decoded.forEach(this::restore));
    }

    private <T> T portableValue(CompoundTag tag, Key<T> key) {
        return tag.contains(key.name())
            ? key.codec().parse(NbtOps.INSTANCE, tag.get(key.name())).result()
                .orElse(key.initial())
            : key.initial();
    }

    public void clearPortable() {
        update(() -> portable.forEach(key -> restore(key, key.initial())));
    }

    public boolean hasPortableContent() {
        return portable.stream()
            .anyMatch(key -> !Objects.equals(values.get(key), key.initial()));
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Key<T> key) {
        require(key);
        if (reading != null && !reading.contains(key))
            throw new IllegalArgumentException(
                "Undeclared derived dependency: " + key.name());
        refresh(key);
        return key.copy().apply((T) values.get(key));
    }

    public <T> void set(Key<T> key, T value) {
        require(key);
        if (reading != null || derived.containsKey(key))
            throw new IllegalStateException("Derived variables are read-only");
        if (depth == 0)
            throw new IllegalStateException(
                "State mutation requires a host operation");
        assign(key, value);
    }

    private <T> void assign(Key<T> key, T value) {
        var copy = key.copy().apply(Objects.requireNonNull(value));
        key.codec().encodeStart(NbtOps.INSTANCE, copy).getOrThrow();
        if (!Objects.equals(values.get(key), copy)) {
            values.put(key, copy);
            changed.add(key);
            invalidate(key);
        }
    }

    private void invalidate(Key<?> source) {
        derived.forEach((key, rule) -> {
            if (rule.dependencies().contains(source) && dirty.add(key))
                invalidate(key);
        });
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
        if (reading != null)
            throw new IllegalStateException(
                "Derived computations cannot mutate state");
        depth++;
        try {
            operation.run();
        } finally {
            try {
                if (depth == 1)
                    derived.keySet().forEach(this::refresh);
            } finally {
                depth--;
            }
            if (depth == 0 && !changed.isEmpty()) {
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
            if (client ? key.synchronize() : !derived.containsKey(key))
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
            if (client ? key.synchronize() : !derived.containsKey(key))
                decoded.put(key, tag.contains(key.name())
                    ? key.codec().parse(NbtOps.INSTANCE, tag.get(key.name()))
                        .getOrThrow()
                    : key.initial());
        update(() -> {
            decoded.forEach(this::restore);
            if (client)
                dirty.clear(); // The server supplies derived UI values,
                               // including private dependencies.
            else
                dirty.addAll(derived.keySet());
        });
    }

    @SuppressWarnings("unchecked")
    private <T> void restore(Key<T> key, Object value) {
        assign(key, (T) value);
    }

    public void defineVisualVariables(VisualVariables.Builder builder) {
        for (var key : keys.values())
            if (key.visual())
                builder.observedBy(signals.get(key)).add(key.name(),
                    key.initial(), () -> values.get(key));
    }
}
