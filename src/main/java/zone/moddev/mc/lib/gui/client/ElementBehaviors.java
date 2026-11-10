package zone.moddev.mc.lib.gui.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import zone.moddev.mc.lib.gui.model.Element;

/**
 * Public client factory registry. Exact types override the most-specific
 * registered base.
 */
public final class ElementBehaviors {
    public static final ElementBehaviors GLOBAL = builtins();

    @FunctionalInterface
    public interface Factory<E extends Element> {
        ElementBehavior create(E element, ElementAccess access);
    }

    private final Map<Class<? extends Element>, java.util.function.Function<ElementAccess, ElementBehavior>> factories = new HashMap<>();

    public static ElementBehaviors builtins() {
        var registry = new ElementBehaviors();
        BuiltinElementClients.registerBehaviors(registry);
        return registry;
    }

    public synchronized <E extends Element> void register(Class<E> type,
        Factory<E> factory) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(factory);
        if (factories.putIfAbsent(type, access -> factory
            .create(type.cast(access.element()), access)) != null)
            throw new IllegalArgumentException(
                "Duplicate element behavior: " + type.getName());
    }

    public synchronized <E extends Element> void replace(Class<E> type,
        Factory<E> factory) {
        Objects.requireNonNull(factory);
        if (!factories.containsKey(type))
            throw new IllegalArgumentException(
                "Missing element behavior: " + type.getName());
        factories.put(type,
            access -> factory.create(type.cast(access.element()), access));
    }

    public synchronized ElementBehavior create(ElementAccess access) {
        var type = access.element().getClass();
        var exact = factories.get(type);
        if (exact != null)
            return Objects.requireNonNull(exact.apply(access));
        var candidates = factories.keySet().stream()
            .filter(key -> key.isAssignableFrom(type)).toList();
        var specific = candidates.stream().filter(key -> candidates.stream()
            .noneMatch(other -> key != other && key.isAssignableFrom(other)))
            .toList();
        if (specific.size() > 1)
            throw new IllegalArgumentException(
                "Ambiguous element behaviors for " + type.getName() + ": "
                    + specific.stream().map(Class::getName).sorted().toList());
        return specific.isEmpty()
            ? new DefaultElementBehavior(access)
            : Objects.requireNonNull(
                factories.get(specific.getFirst()).apply(access));
    }
}
