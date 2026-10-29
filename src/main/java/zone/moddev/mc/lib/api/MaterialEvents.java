package zone.moddev.mc.lib.api;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import com.google.gson.JsonParser;

import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;

import zone.moddev.mc.lib.common.codecs.EventExtension;
import zone.moddev.mc.lib.common.types.MMDLibEventCallback;
import zone.moddev.mc.lib.common.types.MMDMaterial;

/** Typed event/callback registration and explicit dispatch for material event bindings. */
public final class MaterialEvents {
  private final ConcurrentHashMap<String, MaterialEvent<?>> events = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<ResourceLocation, MMDLibEventCallback<?>> callbacks =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, MethodContract<?>> contracts = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<ResourceLocation, ResolvedEventHandler<?>> resolved =
      new ConcurrentHashMap<>();

  private record MethodContract<C>(
      MaterialEvent<C> event, MethodType signature, MethodEventInvoker<C> invoker) {}

  /** Creates a registry with the built-in event contracts from {@link MaterialEventTypes}. */
  public MaterialEvents() {
    MaterialEventTypes.install(this);
  }

  /**
   * Registers an event whose method handlers take the context object itself as their only
   * parameter.
   *
   * @param name the JSON name; must be unique
   * @param contextType the context class passed when the event fires
   * @return the key to use for callbacks and dispatch
   * @throws IllegalArgumentException if the name is already registered
   */
  public <C> MaterialEvent<C> registerEvent(String name, Class<C> contextType) {
    return registerEvent(
        name,
        contextType,
        MethodType.methodType(void.class, contextType),
        (method, context) -> {
          method.invoke(context);
        });
  }

  /**
   * Registers an event whose method handlers take parameters other than the context, e.g. {@code
   * (Player, LivingEntity)} for {@code weapon-hit}.
   *
   * @param signature the parameter types an extension file's {@code public static void} method must
   *     declare; the return type must be {@code void}
   * @param invoker unpacks the context into those parameters
   * @throws IllegalArgumentException if the name is already registered or the signature does not
   *     return void
   */
  public synchronized <C> MaterialEvent<C> registerEvent(
      String name, Class<C> contextType, MethodType signature, MethodEventInvoker<C> invoker) {
    Objects.requireNonNull(signature);
    Objects.requireNonNull(invoker);
    if (signature.returnType() != void.class)
      throw new IllegalArgumentException("Event handlers must return void");
    var key = new MaterialEvent<>(name, contextType);
    if (events.putIfAbsent(name, key) != null)
      throw new IllegalArgumentException("Event already registered: " + name);
    contracts.put(name, new MethodContract<>(key, signature, invoker));
    return key;
  }

  /**
   * Registers a Java callback under {@code id}, so JSON {@code event} maps can bind it. Each ID
   * handles exactly one event.
   *
   * @throws IllegalArgumentException if the event is not registered here or the ID is taken
   */
  public synchronized <C> void registerCallback(
      ResourceLocation id, MaterialEvent<C> event, Consumer<? super C> callback) {
    if (!event.equals(events.get(event.name())))
      throw new IllegalArgumentException("Event is not registered here: " + event.name());
    if (callbacks.putIfAbsent(
            Objects.requireNonNull(id), new MMDLibEventCallback<>(event, callback))
        != null) throw new IllegalArgumentException("Callback already registered: " + id);
  }

  /**
   * As {@link #loadExtension(EventExtension, ClassLoader, String)}, naming the source {@code
   * <extension>} in errors.
   */
  public List<ResolvedEventHandler<?>> loadExtension(EventExtension extension, ClassLoader loader) {
    return loadExtension(extension, loader, "<extension>");
  }

  /**
   * Resolves and registers every handler in an extension file. All methods are resolved before any
   * is installed, so a file with one bad handler installs none.
   *
   * @param loader the class loader used to find handler classes, normally the owning mod's
   * @param source names the file in error messages
   * @return the handlers installed
   * @throws IllegalArgumentException for duplicate IDs, unknown event types, or methods that cannot
   *     be found with the event's signature
   */
  public synchronized List<ResolvedEventHandler<?>> loadExtension(
      EventExtension extension, ClassLoader loader, String source) {
    Objects.requireNonNull(extension);
    Objects.requireNonNull(loader);
    Objects.requireNonNull(source);
    var pending = new java.util.LinkedHashMap<ResourceLocation, ResolvedEventHandler<?>>();
    for (var definition : extension.events()) {
      if (callbacks.containsKey(definition.id()) || pending.containsKey(definition.id()))
        throw new IllegalArgumentException(source + ": Duplicate callback ID: " + definition.id());
      var contract = contracts.get(definition.type());
      if (contract == null)
        throw new IllegalArgumentException(
            source + ": Unknown event type " + definition.type() + " for " + definition.id());
      pending.put(definition.id(), resolve(definition, contract, loader, source));
    }
    pending.forEach(
        (id, binding) -> {
          callbacks.put(id, binding.callback());
          resolved.put(id, binding);
        });
    return List.copyOf(pending.values());
  }

  /**
   * Reads and loads an extension file from disk.
   *
   * @throws IOException if the file cannot be read
   */
  public List<ResolvedEventHandler<?>> loadExtension(Path file, ClassLoader loader)
      throws IOException {
    final EventExtension extension;
    try (var reader = Files.newBufferedReader(file)) {
      extension =
          EventExtension.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException(file + ": " + ex.getMessage(), ex);
    }
    return loadExtension(extension, loader, file.toString());
  }

  private static <C> ResolvedEventHandler<C> resolve(
      EventExtension.Handler definition,
      MethodContract<C> contract,
      ClassLoader loader,
      String source) {
    String reference = definition.handler();
    int separator = reference.indexOf('#');
    if (separator <= 0
        || separator == reference.length() - 1
        || separator != reference.lastIndexOf('#'))
      throw new IllegalArgumentException(source + ": Expected Class#method for " + definition.id());
    final MethodHandle handle;
    try {
      var owner = Class.forName(reference.substring(0, separator), false, loader);
      handle =
          MethodHandles.publicLookup()
              .findStatic(owner, reference.substring(separator + 1), contract.signature());
    } catch (ReflectiveOperationException | LinkageError | SecurityException ex) {
      throw new IllegalArgumentException(
          source
              + ": Cannot resolve "
              + definition.id()
              + " handler "
              + reference
              + "; expected public static "
              + contract.signature(),
          ex);
    }
    var callback =
        new MMDLibEventCallback<C>(
            contract.event(),
            context -> {
              try {
                contract.invoker().invoke(handle, context);
              } catch (RuntimeException | Error ex) {
                throw ex;
              } catch (Throwable ex) {
                throw new IllegalStateException(
                    "Event handler failed: " + definition.id() + " (" + reference + ")", ex);
              }
            });
    return new ResolvedEventHandler<>(definition, handle, callback);
  }

  /**
   * Every handler loaded from extension files so far, by ID. Code-registered callbacks are not
   * included.
   */
  public Map<ResourceLocation, ResolvedEventHandler<?>> resolvedHandlers() {
    return Map.copyOf(resolved);
  }

  /**
   * The extension-file handler with this ID.
   *
   * @throws IllegalArgumentException if there is none
   */
  public ResolvedEventHandler<?> resolvedHandler(ResourceLocation id) {
    var handler = resolved.get(id);
    if (handler == null) throw new IllegalArgumentException("Unknown method handler: " + id);
    return handler;
  }

  /**
   * Runs the callback {@code id} for {@code event}. The callback was resolved at load, so no method
   * lookup happens here.
   *
   * @throws IllegalArgumentException if the event or callback is unknown, or the callback handles
   *     another event
   */
  public <C> void dispatch(ResourceLocation id, MaterialEvent<C> event, C context) {
    if (!event.equals(events.get(event.name())))
      throw new IllegalArgumentException("Unknown event key: " + event.name());
    event.contextType().cast(Objects.requireNonNull(context));
    var callback = callbacks.get(id);
    if (callback == null) throw new IllegalArgumentException("Unknown callback: " + id);
    callback.invokeChecked(event, context);
  }

  /** Every registered callback by ID, both code-registered and from extension files. */
  public Map<ResourceLocation, MMDLibEventCallback<?>> callbacks() {
    return Map.copyOf(callbacks);
  }

  /**
   * Checks a JSON {@code event} map: every event name is registered, and every callback exists and
   * handles that event.
   *
   * @throws IllegalArgumentException naming the first bad entry
   */
  public void validate(Map<String, ResourceLocation> bindings) {
    bindings.forEach(
        (event, id) -> {
          var key = events.get(event);
          if (key == null) throw new IllegalArgumentException("Unknown material event: " + event);
          var callback = callbacks.get(id);
          if (callback == null) throw new IllegalArgumentException("Unknown callback: " + id);
          if (!key.equals(callback.event()))
            throw new IllegalArgumentException("Callback " + id + " does not handle " + event);
        });
  }

  /**
   * Runs the callback {@code material} binds to {@code event}, if any. A material with no binding
   * for the event is skipped silently.
   */
  public <C> void dispatch(MMDMaterial material, MaterialEvent<C> event, C context) {
    if (!event.equals(events.get(event.name())))
      throw new IllegalArgumentException("Unknown event key: " + event.name());
    event.contextType().cast(Objects.requireNonNull(context));
    var id = material.events().get(event.name());
    if (id == null) return;
    var callback = callbacks.get(id);
    if (callback == null) throw new IllegalArgumentException("Unknown callback: " + id);
    callback.invokeChecked(event, context);
  }

  /**
   * Bridges a NeoForge event straight to material bindings: whenever {@code event}'s context type
   * is posted on {@code bus}, each material from {@code materials} is dispatched once.
   *
   * @param materials selects the affected materials, e.g. from the player's equipped items
   * @return closes the bridge by unregistering the listener
   */
  public <E extends Event> AutoCloseable listen(
      IEventBus bus,
      MaterialEvent<E> event,
      Function<? super E, ? extends Iterable<MMDMaterial>> materials) {
    if (!event.equals(events.get(event.name())))
      throw new IllegalArgumentException("Unknown event key");
    Consumer<E> listener =
        context -> {
          var seen = new java.util.HashSet<MMDMaterial>();
          for (var material : materials.apply(context))
            if (seen.add(material)) dispatch(material, event, context);
        };
    bus.addListener(event.contextType(), listener);
    return () -> bus.unregister(listener);
  }
}
