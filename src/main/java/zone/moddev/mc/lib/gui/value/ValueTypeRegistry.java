package zone.moddev.mc.lib.gui.value;

import java.nio.charset.StandardCharsets;
import java.util.*;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Common-side registry used by binding, snapshots, deltas and wire decoding. */
public final class ValueTypeRegistry {
  public static final ValueTypeRegistry GLOBAL = builtins();
  private final Map<ResourceLocation, ValueType<?>> types = new TreeMap<>();
  private final Map<Class<?>, ValueType<?>> javaTypes = new HashMap<>();
  private final Map<Class<?>, Optional<ValueType<?>>> resolved = new HashMap<>();
  private boolean frozen;

  public static ValueTypeRegistry builtins() {
    var registry = new ValueTypeRegistry();
    ValueTypes.register(registry);
    return registry;
  }

  public synchronized <T> ValueType<T> register(ValueType<T> type) {
    Objects.requireNonNull(type);
    if (frozen)
      throw new IllegalStateException(
          "GUI value types are frozen; register during mod construction");
    if (types.containsKey(type.id()))
      throw new IllegalArgumentException("Duplicate GUI value type: " + type.id());
    for (var javaType : type.javaTypes())
      if (javaTypes.containsKey(javaType))
        throw new IllegalArgumentException("Duplicate GUI Java type: " + javaType.getName());
    types.put(type.id(), type);
    type.javaTypes().forEach(javaType -> javaTypes.put(javaType, type));
    resolved.clear();
    return type;
  }

  public synchronized void freeze() {
    frozen = true;
  }

  public synchronized ValueType<?> require(ResourceLocation id) {
    var type = types.get(id);
    if (type == null) throw new IllegalArgumentException("Unknown GUI value type: " + id);
    return type;
  }

  /** All registered getter types; wire-only envelopes are excluded. */
  public synchronized Set<ValueType<?>> bindingTypes() {
    var result = new LinkedHashSet<ValueType<?>>();
    types.values().stream().filter(ValueType::bindable).forEach(result::add);
    return Collections.unmodifiableSet(result);
  }

  public Optional<ValueType<?>> binding(Class<?> javaType) {
    return resolve(javaType).filter(ValueType::bindable);
  }

  /**
   * Exact Java bindings win, then the most specific superclass/interface; ambiguity is rejected.
   */
  public synchronized Optional<ValueType<?>> resolve(Class<?> javaType) {
    var cached = resolved.get(javaType);
    if (cached != null) return cached;
    var exact = javaTypes.get(javaType);
    if (exact != null) return Optional.of(exact);
    var matches =
        javaTypes.keySet().stream()
            .filter(c -> javaTypes.get(c).subtypes() && c.isAssignableFrom(javaType))
            .toList();
    var specific =
        matches.stream()
            .filter(
                c -> matches.stream().noneMatch(other -> c != other && c.isAssignableFrom(other)))
            .map(javaTypes::get)
            .distinct()
            .sorted(Comparator.comparing(ValueType::id))
            .toList();
    if (specific.size() > 1)
      throw new IllegalArgumentException(
          "Ambiguous GUI value type for " + javaType.getName() + ": " + specific);
    var result = specific.stream().findFirst();
    resolved.put(javaType, result);
    return result;
  }

  private ValueType<?> typeOf(Object value) {
    return resolve(value.getClass())
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Unsupported GUI value: " + value.getClass().getName()));
  }

  public Object snapshot(Object value, RegistryAccess registries) {
    return value == null ? null : typeOf(value).snapshot(value, registries);
  }

  public boolean equal(Object left, Object right) {
    if (left == null || right == null) return left == right;
    var type = typeOf(left);
    return type == typeOf(right) && type.equal(left, right);
  }

  public void write(RegistryFriendlyByteBuf buffer, Object value) {
    buffer.writeBoolean(value != null);
    if (value == null) return;
    var type = typeOf(value);
    var bytes = type.encode(buffer.registryAccess(), value);
    buffer.writeResourceLocation(type.id());
    buffer.writeVarInt(type.version());
    buffer.writeByteArray(bytes);
  }

  public Object read(RegistryFriendlyByteBuf buffer) {
    if (!buffer.readBoolean()) return null;
    var type = require(buffer.readResourceLocation());
    if (buffer.readVarInt() != type.version())
      throw new IllegalArgumentException("GUI value schema mismatch: " + type.id());
    return type.decode(buffer.registryAccess(), buffer.readByteArray(type.maxBytes()));
  }

  /**
   * Deterministic across registration order; codec changes require the declared version to change.
   */
  public synchronized String fingerprint() {
    try {
      var schema = types.values().stream().map(ValueType::schema).toList().toString();
      return HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(schema.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }
}
