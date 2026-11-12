package zone.moddev.mc.lib.machine.presentation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import zone.moddev.mc.lib.api.util.ChangeSignal;

/**
 * Typed declarations and immutable snapshots, independent of networking and
 * rendering.
 */
public final class VisualVariables {
    public enum Kind {
        BOOLEAN, NUMBER, TEXT;

        public static Kind of(Object value) {
            if (value instanceof Boolean)
                return BOOLEAN;
            if (value instanceof Number number
                && Double.isFinite(number.doubleValue()))
                return NUMBER;
            if (value instanceof String text && text.length() <= 256)
                return TEXT;
            throw new IllegalArgumentException(
                "Invalid visual value: " + value);
        }
    }

    private record Entry(Object fallback, Supplier<?> value,
        List<ChangeSignal> sources, ChangeSignal manual) {
    }

    /**
     * Explicit invalidation for declarations without a component change signal.
     */
    public void changed(String name) {
        var entry = entries.get(name);
        if (entry == null)
            throw new IllegalArgumentException(
                "Unknown visual variable " + name);
        entry.manual().changed();
    }

    /**
     * Observe only referenced declarations; the returned action removes every
     * subscription.
     */
    public Runnable subscribe(Set<String> names, Consumer<String> changed) {
        var removers = new ArrayList<Runnable>();
        for (String name : names) {
            var entry = entries.get(name);
            if (entry == null)
                throw new IllegalArgumentException(
                    "Unknown visual variable " + name);
            removers.add(entry.manual().subscribe(() -> changed.accept(name)));
            for (var source : entry.sources())
                removers.add(source.subscribe(() -> changed.accept(name)));
        }
        return () -> removers.forEach(Runnable::run);
    }

    private final Map<String, Entry> entries;

    private VisualVariables(Map<String, Entry> entries) {
        this.entries = Map.copyOf(entries);
    }

    public Map<String, Object> defaults() {
        var result = new LinkedHashMap<String, Object>();
        entries.forEach((name, entry) -> result.put(name, entry.fallback()));
        return Map.copyOf(result);
    }

    /** Sample only the variables referenced by this machine's appearance. */
    public Map<String, Object> snapshot(Set<String> names) {
        var result = new LinkedHashMap<String, Object>();
        for (String name : names) {
            var entry = entries.get(name);
            if (entry == null)
                throw new IllegalArgumentException(
                    "Unknown visual variable " + name);
            Object value = normalize(entry.value().get());
            if (Kind.of(value) != Kind.of(entry.fallback()))
                throw new IllegalArgumentException(
                    "Visual variable changed type: " + name);
            result.put(name, value);
        }
        return Map.copyOf(result);
    }

    private static Object normalize(Object value) {
        Kind.of(value);
        return value instanceof Number number ? number.doubleValue() : value;
    }

    public static final class Builder {
        private final Map<String, Entry> entries;
        private final String prefix;
        private final List<ChangeSignal> sources;

        public Builder() {
            this(new LinkedHashMap<>(), "", List.of());
        }

        private Builder(Map<String, Entry> entries, String prefix,
            List<ChangeSignal> sources) {
            this.entries = entries;
            this.prefix = prefix;
            this.sources = sources;
        }

        /**
         * Change sources for subsequent declarations in this scope; getters are
         * not polled.
         */
        public Builder observedBy(ChangeSignal... sources) {
            return new Builder(entries, prefix, List.of(sources));
        }

        public Builder add(String name, Object fallback, Supplier<?> value) {
            String key = prefix + name;
            if (key.isBlank() || key.length() > 256
                || !key.matches("[a-zA-Z0-9_.:/-]+"))
                throw new IllegalArgumentException(
                    "Invalid visual variable name " + key);
            if (entries.size() >= 256)
                throw new IllegalArgumentException("Too many visual variables");
            if (entries.putIfAbsent(key,
                new Entry(normalize(fallback),
                    java.util.Objects.requireNonNull(value), sources,
                    new ChangeSignal())) != null)
                throw new IllegalArgumentException(
                    "Duplicate visual variable " + key);
            return this;
        }

        public Builder component(String name, VisualVariableProvider provider) {
            provider.defineVisualVariables(
                new Builder(entries, prefix + name + ".", sources));
            return this;
        }

        public VisualVariables build() {
            return new VisualVariables(entries);
        }
    }
}
