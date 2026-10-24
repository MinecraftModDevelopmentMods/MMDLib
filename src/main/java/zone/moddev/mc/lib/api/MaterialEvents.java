package zone.moddev.mc.lib.api;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import zone.moddev.mc.lib.common.types.MMDLibEventCallback;
import zone.moddev.mc.lib.common.types.MMDMaterial;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/** Typed event/callback registration and explicit dispatch for material event bindings. */
public final class MaterialEvents {
    private final ConcurrentHashMap<String, MaterialEvent<?>> events = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ResourceLocation, MMDLibEventCallback<?>> callbacks = new ConcurrentHashMap<>();
    public <C> MaterialEvent<C> registerEvent(String name, Class<C> contextType) {
        var key = new MaterialEvent<>(name, contextType);
        if (events.putIfAbsent(name, key) != null) throw new IllegalArgumentException("Event already registered: " + name);
        return key;
    }
    public <C> void registerCallback(ResourceLocation id, MaterialEvent<C> event, Consumer<? super C> callback) {
        if (!event.equals(events.get(event.name()))) throw new IllegalArgumentException("Event is not registered here: " + event.name());
        if (callbacks.putIfAbsent(Objects.requireNonNull(id), new MMDLibEventCallback<>(event, callback)) != null)
            throw new IllegalArgumentException("Callback already registered: " + id);
    }
    public Map<ResourceLocation, MMDLibEventCallback<?>> callbacks() { return Map.copyOf(callbacks); }
    public void validate(Map<String, ResourceLocation> bindings) {
        bindings.forEach((event, id) -> {
            var key = events.get(event);
            if (key == null) throw new IllegalArgumentException("Unknown material event: " + event);
            var callback = callbacks.get(id);
            if (callback == null) throw new IllegalArgumentException("Unknown callback: " + id);
            if (!key.equals(callback.event())) throw new IllegalArgumentException("Callback " + id + " does not handle " + event);
        });
    }
    public <C> void dispatch(MMDMaterial material, MaterialEvent<C> event, C context) {
        if (!event.equals(events.get(event.name()))) throw new IllegalArgumentException("Unknown event key: " + event.name());
        event.contextType().cast(Objects.requireNonNull(context));
        var id = material.events().get(event.name());
        if (id == null) return;
        var callback = callbacks.get(id);
        if (callback == null) throw new IllegalArgumentException("Unknown callback: " + id);
        callback.invokeChecked(event, context);
    }
    /** Resolver selects the applicable materials, e.g. from the player's equipped items. */
    public <E extends Event> AutoCloseable listen(IEventBus bus, MaterialEvent<E> event,
            Function<? super E, ? extends Iterable<MMDMaterial>> materials) {
        if (!event.equals(events.get(event.name()))) throw new IllegalArgumentException("Unknown event key");
        Consumer<E> listener = context -> {
            var seen = new java.util.HashSet<MMDMaterial>();
            for (var material : materials.apply(context)) if (seen.add(material)) dispatch(material, event, context);
        };
        bus.addListener(event.contextType(), listener);
        return () -> bus.unregister(listener);
    }
}
