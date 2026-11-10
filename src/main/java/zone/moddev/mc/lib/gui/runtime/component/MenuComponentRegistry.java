package zone.moddev.mc.lib.gui.runtime.component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.world.entity.player.Inventory;

import zone.moddev.mc.lib.gui.runtime.MachineMenu;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;

/** Common-side component factories, registered during mod construction. */
public final class MenuComponentRegistry {
    public static final MenuComponentRegistry GLOBAL = builtins();

    public static final class Context {
        private final MachineMenu menu;
        private final Inventory inventory;
        private final ScreenHost host;
        private final boolean client;
        private final Map<Class<?>, Object> services = new HashMap<>();

        public Context(MachineMenu menu, Inventory inventory, ScreenHost host, boolean client) {
            this.menu = Objects.requireNonNull(menu);
            this.inventory = Objects.requireNonNull(inventory);
            this.host = host;
            this.client = client;
        }

        public MachineMenu menu() {
            return menu;
        }

        public Inventory inventory() {
            return inventory;
        }

        public ScreenHost host() {
            return host;
        }

        public boolean client() {
            return client;
        }

        public <T> T shared(Class<T> type, Supplier<T> factory) {
            return type.cast(
                services.computeIfAbsent(type, key -> Objects.requireNonNull(factory.get())));
        }
    }

    private record Registration<T extends MenuComponent>(
        MenuComponentKey<T> key,
        int order,
        Function<Context, T> factory,
        List<ComponentSync<?>> fields) {
        MenuComponent create(Context context) {
            var value = factory.apply(context);
            return value == null ? null : key.type().cast(value);
        }
    }

    private final Map<net.minecraft.resources.ResourceLocation, Registration<?>> registrations = new HashMap<>();
    private final List<java.lang.ref.WeakReference<MenuComponent>> issued = new ArrayList<>();

    public static MenuComponentRegistry builtins() {
        var registry = new MenuComponentRegistry();
        BuiltinMenuComponents.register(registry);
        return registry;
    }

    /**
     * Registers a factory and its complete synchronized field declarations on both logical sides.
     */
    public synchronized <T extends MenuComponent> void register(
        MenuComponentKey<T> key,
        int order,
        Function<Context, T> factory,
        ComponentSync<?>... fields) {
        Objects.requireNonNull(factory);
        if (registrations.putIfAbsent(
            key.id(), new Registration<>(key, order, factory, declarations(fields))) != null)
            throw new IllegalArgumentException("Duplicate menu component: " + key.id());
    }

    /** Replaces both the factory and its complete field list for an existing typed key. */
    public synchronized <T extends MenuComponent> void replace(
        MenuComponentKey<T> key,
        int order,
        Function<Context, T> factory,
        ComponentSync<?>... fields) {
        var previous = registrations.get(key.id());
        if (previous == null || !previous.key().equals(key))
            throw new IllegalArgumentException("Unknown menu component key: " + key.id());
        registrations.put(
            key.id(),
            new Registration<>(key, order, Objects.requireNonNull(factory), declarations(fields)));
    }

    private static List<ComponentSync<?>> declarations(ComponentSync<?>[] fields) {
        var names = new HashSet<net.minecraft.resources.ResourceLocation>();
        for (var field : fields)
            if (!names.add(field.id()))
                throw new IllegalArgumentException("Duplicate component field: " + field.id());
        return Arrays.stream(fields)
            .sorted(Comparator.comparing(field -> field.id().toString()))
            .toList();
    }

    /** Stable address order, including fields whose component is absent from a particular menu. */
    public synchronized List<ComponentStates.Declaration> schema() {
        return registrations.values().stream()
            .sorted(Comparator.comparing(r -> r.key().id().toString()))
            .flatMap(
                r -> r.fields().stream()
                    .map(field -> new ComponentStates.Declaration(r.key(), field)))
            .toList();
    }

    private List<Registration<?>> ordered() {
        return registrations.values().stream()
            .sorted(
                Comparator.<Registration<?>>comparingInt(Registration::order)
                    .thenComparing(r -> r.key().id().toString()))
            .toList();
    }

    public synchronized String fingerprint() {
        return ordered().stream()
            .map(
                r -> r.key().id()
                    + "/"
                    + r.key().type().getName()
                    + "/"
                    + r.order()
                    + "/"
                    + r.fields().stream().map(ComponentSync::fingerprint).toList())
            .toList()
            .toString();
    }

    public synchronized MenuComponents create(Context context) {
        var result = new LinkedHashMap<MenuComponentKey<?>, MenuComponent>();
        for (var registration : ordered()) {
            var component = registration.create(context);
            if (component == null)
                continue;
            issued.removeIf(reference -> reference.get() == null);
            if (issued.stream().anyMatch(reference -> reference.get() == component))
                throw new IllegalArgumentException(
                    "Menu component factories must return fresh instances: "
                        + registration.key().id());
            issued.add(new java.lang.ref.WeakReference<>(component));
            result.put(registration.key(), component);
        }
        return new MenuComponents(
            context.menu(),
            result,
            new ComponentStates(schema(), result, context.inventory().player.registryAccess()));
    }
}
