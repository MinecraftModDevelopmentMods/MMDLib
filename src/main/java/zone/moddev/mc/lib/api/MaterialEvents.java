package zone.moddev.mc.lib.api;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import zone.moddev.mc.lib.common.types.MMDLibEventCallback;
import zone.moddev.mc.lib.common.types.MMDMaterial;
import java.util.Map;
import java.util.List;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import zone.moddev.mc.lib.common.codecs.EventExtension;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/** Typed event/callback registration and explicit dispatch for material event bindings. */
public final class MaterialEvents {
    private final ConcurrentHashMap<String, MaterialEvent<?>> events = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ResourceLocation, MMDLibEventCallback<?>> callbacks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MethodContract<?>> contracts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ResourceLocation, ResolvedEventHandler<?>> resolved = new ConcurrentHashMap<>();
    private record MethodContract<C>(MaterialEvent<C> event, MethodType signature, MethodEventInvoker<C> invoker) {}
    public MaterialEvents() { MaterialEventTypes.install(this); }
    public <C> MaterialEvent<C> registerEvent(String name, Class<C> contextType) {
        return registerEvent(name, contextType, MethodType.methodType(void.class, contextType),
                (method, context) -> { method.invoke(context); });
    }
    /** Signature describes the public static void method in JSON; invoker extracts its arguments from context. */
    public synchronized <C> MaterialEvent<C> registerEvent(String name, Class<C> contextType,
            MethodType signature, MethodEventInvoker<C> invoker) {
        Objects.requireNonNull(signature); Objects.requireNonNull(invoker);
        if (signature.returnType() != void.class) throw new IllegalArgumentException("Event handlers must return void");
        var key = new MaterialEvent<>(name, contextType);
        if (events.putIfAbsent(name, key) != null) throw new IllegalArgumentException("Event already registered: " + name);
        contracts.put(name, new MethodContract<>(key, signature, invoker));
        return key;
    }
    public synchronized <C> void registerCallback(ResourceLocation id, MaterialEvent<C> event, Consumer<? super C> callback) {
        if (!event.equals(events.get(event.name()))) throw new IllegalArgumentException("Event is not registered here: " + event.name());
        if (callbacks.putIfAbsent(Objects.requireNonNull(id), new MMDLibEventCallback<>(event, callback)) != null)
            throw new IllegalArgumentException("Callback already registered: " + id);
    }
    public List<ResolvedEventHandler<?>> loadExtension(EventExtension extension, ClassLoader loader) {
        return loadExtension(extension, loader, "<extension>");
    }
    /** Resolve every method first. A failed file installs none of its callbacks. */
    public synchronized List<ResolvedEventHandler<?>> loadExtension(EventExtension extension, ClassLoader loader, String source) {
        Objects.requireNonNull(extension); Objects.requireNonNull(loader); Objects.requireNonNull(source);
        var pending = new java.util.LinkedHashMap<ResourceLocation, ResolvedEventHandler<?>>();
        for (var definition : extension.events()) {
            if (callbacks.containsKey(definition.id()) || pending.containsKey(definition.id()))
                throw new IllegalArgumentException(source + ": Duplicate callback ID: " + definition.id());
            var contract = contracts.get(definition.type());
            if (contract == null) throw new IllegalArgumentException(source + ": Unknown event type " + definition.type() + " for " + definition.id());
            pending.put(definition.id(), resolve(definition, contract, loader, source));
        }
        pending.forEach((id, binding) -> { callbacks.put(id, binding.callback()); resolved.put(id, binding); });
        return List.copyOf(pending.values());
    }
    public List<ResolvedEventHandler<?>> loadExtension(Path file, ClassLoader loader) throws IOException {
        final EventExtension extension;
        try (var reader = Files.newBufferedReader(file)) {
            extension = EventExtension.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(file + ": " + ex.getMessage(), ex);
        }
        return loadExtension(extension, loader, file.toString());
    }
    private static <C> ResolvedEventHandler<C> resolve(EventExtension.Handler definition, MethodContract<C> contract,
            ClassLoader loader, String source) {
        String reference = definition.handler(); int separator = reference.indexOf('#');
        if (separator <= 0 || separator == reference.length() - 1 || separator != reference.lastIndexOf('#'))
            throw new IllegalArgumentException(source + ": Expected Class#method for " + definition.id());
        final MethodHandle handle;
        try {
            var owner = Class.forName(reference.substring(0, separator), false, loader);
            handle = MethodHandles.publicLookup().findStatic(owner, reference.substring(separator + 1), contract.signature());
        } catch (ReflectiveOperationException | LinkageError | SecurityException ex) {
            throw new IllegalArgumentException(source + ": Cannot resolve " + definition.id() + " handler " + reference
                    + "; expected public static " + contract.signature(), ex);
        }
        var callback = new MMDLibEventCallback<C>(contract.event(), context -> {
            try { contract.invoker().invoke(handle, context); }
            catch (RuntimeException | Error ex) { throw ex; }
            catch (Throwable ex) { throw new IllegalStateException("Event handler failed: " + definition.id() + " (" + reference + ")", ex); }
        });
        return new ResolvedEventHandler<>(definition, handle, callback);
    }
    public Map<ResourceLocation, ResolvedEventHandler<?>> resolvedHandlers() { return Map.copyOf(resolved); }
    public ResolvedEventHandler<?> resolvedHandler(ResourceLocation id) {
        var handler = resolved.get(id);
        if (handler == null) throw new IllegalArgumentException("Unknown method handler: " + id);
        return handler;
    }
    /** ID selects the already-resolved callback; no method lookup occurs during dispatch. */
    public <C> void dispatch(ResourceLocation id, MaterialEvent<C> event, C context) {
        if (!event.equals(events.get(event.name()))) throw new IllegalArgumentException("Unknown event key: " + event.name());
        event.contextType().cast(Objects.requireNonNull(context));
        var callback = callbacks.get(id);
        if (callback == null) throw new IllegalArgumentException("Unknown callback: " + id);
        callback.invokeChecked(event, context);
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
