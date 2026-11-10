package zone.moddev.mc.lib.gui.value;

import java.util.*;
import java.util.function.*;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import io.netty.buffer.Unpooled;

/** A registered GUI value's Java bindings, canonical representation and bounded wire contract. */
public final class ValueType<T> {
  public static final int MAX_BYTES = 1 << 20;
  private final ResourceLocation id;
  private final Class<?> representation;
  private final Set<Class<?>> javaTypes;
  private final int version, maxBytes;
  private final String description;
  private final boolean bindable, subtypes;
  private final Supplier<StreamCodec<RegistryFriendlyByteBuf, T>> codec;
  private final Function<Object, T> normalize;
  private final BiFunction<RegistryAccess, T, T> copy;
  private final BiPredicate<T, T> equality;
  private final Predicate<T> valid;

  private ValueType(Builder<T> b) {
    id = b.id;
    representation = b.representation;
    javaTypes = Set.copyOf(b.javaTypes);
    version = b.version;
    maxBytes = b.maxBytes;
    description = b.description;
    bindable = b.bindable;
    subtypes = b.subtypes;
    codec = b.codec;
    normalize = b.normalize;
    copy = b.copy;
    equality = b.equality;
    valid = b.valid;
  }

  /** Codecs are lazy so declaring types does not initialize Minecraft registries during loading. */
  public static <T> Builder<T> builder(
      ResourceLocation id,
      Class<?> representation,
      Supplier<StreamCodec<RegistryFriendlyByteBuf, T>> codec,
      int maxBytes) {
    return new Builder<>(id, representation, codec, maxBytes);
  }

  public ResourceLocation id() {
    return id;
  }

  public Set<Class<?>> javaTypes() {
    return javaTypes;
  }

  public int version() {
    return version;
  }

  public int maxBytes() {
    return maxBytes;
  }

  public String description() {
    return description;
  }

  public boolean bindable() {
    return bindable;
  }

  /** Whether registered Java bindings also accept subclasses/implementations. */
  public boolean subtypes() {
    return subtypes;
  }

  public T normalize(Object value) {
    var result = Objects.requireNonNull(normalize.apply(value), "Null GUI value: " + id);
    if (!representation.isInstance(result) || !valid.test(result))
      throw new IllegalArgumentException("Invalid GUI value: " + id);
    return result;
  }

  /** Detach mutable data from its owner. The default is a bounded codec round trip. */
  public T snapshot(Object value, RegistryAccess registries) {
    var canonical = normalize(value);
    return copy == null
        ? decode(registries, encode(registries, canonical))
        : normalize(copy.apply(registries, canonical));
  }

  public boolean equal(Object left, Object right) {
    return equality.test(normalize(left), normalize(right));
  }

  public byte[] encode(RegistryAccess registries, Object value) {
    var buffer =
        new RegistryFriendlyByteBuf(Unpooled.buffer(Math.min(64, maxBytes), maxBytes), registries);
    try {
      codec.get().encode(buffer, normalize(value));
      byte[] data = new byte[buffer.readableBytes()];
      buffer.readBytes(data);
      return data;
    } finally {
      buffer.release();
    }
  }

  public T decode(RegistryAccess registries, byte[] data) {
    if (data.length > maxBytes) throw new IllegalArgumentException("GUI value too large: " + id);
    var buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), registries);
    try {
      var value = normalize(codec.get().decode(buffer));
      if (buffer.isReadable()) throw new IllegalArgumentException("Trailing GUI value data: " + id);
      return value;
    } finally {
      buffer.release();
    }
  }

  String schema() {
    return id
        + "/"
        + version
        + "/"
        + maxBytes
        + "/"
        + bindable
        + "/"
        + subtypes
        + "/"
        + representation.getName()
        + "/"
        + javaTypes.stream().map(Class::getName).sorted().toList();
  }

  @Override
  public String toString() {
    return id.toString();
  }

  public static final class Builder<T> {
    private final ResourceLocation id;
    private final Class<?> representation;
    private final Supplier<StreamCodec<RegistryFriendlyByteBuf, T>> codec;
    private final int maxBytes;
    private final Set<Class<?>> javaTypes = new HashSet<>();
    private int version = 1;
    private String description;
    private boolean bindable = true;
    private boolean subtypes = true;
    private Function<Object, T> normalize;
    private BiFunction<RegistryAccess, T, T> copy;
    private BiPredicate<T, T> equality = Objects::equals;
    private Predicate<T> valid = value -> true;

    @SuppressWarnings("unchecked")
    private Builder(
        ResourceLocation id,
        Class<?> representation,
        Supplier<StreamCodec<RegistryFriendlyByteBuf, T>> codec,
        int maxBytes) {
      this.id = Objects.requireNonNull(id);
      this.representation = Objects.requireNonNull(representation);
      this.codec = Objects.requireNonNull(codec);
      if (representation.isPrimitive() || maxBytes < 1 || maxBytes > MAX_BYTES)
        throw new IllegalArgumentException("Invalid GUI value representation or byte limit");
      this.maxBytes = maxBytes;
      description = id.toString();
      javaTypes.add(representation);
      normalize = value -> (T) representation.cast(value);
    }

    /** Additional declared getter/input types normalized into the canonical representation. */
    public Builder<T> javaTypes(Class<?>... types) {
      for (var type : types) javaTypes.add(Objects.requireNonNull(type));
      return this;
    }

    public Builder<T> version(int value) {
      if (value < 1)
        throw new IllegalArgumentException("GUI value schema version must be positive");
      version = value;
      return this;
    }

    public Builder<T> description(String value) {
      description = Objects.requireNonNull(value);
      return this;
    }

    public Builder<T> bindable(boolean value) {
      bindable = value;
      return this;
    }

    /** Disable inheritance when only explicitly listed Java types can be normalized. */
    public Builder<T> subtypes(boolean value) {
      subtypes = value;
      return this;
    }

    public Builder<T> normalize(Function<Object, T> value) {
      normalize = Objects.requireNonNull(value);
      return this;
    }

    public Builder<T> copy(BiFunction<RegistryAccess, T, T> value) {
      copy = Objects.requireNonNull(value);
      return this;
    }

    public Builder<T> immutable() {
      return copy((registries, value) -> value);
    }

    public Builder<T> equality(BiPredicate<T, T> value) {
      equality = Objects.requireNonNull(value);
      return this;
    }

    public Builder<T> validate(Predicate<T> value) {
      valid = Objects.requireNonNull(value);
      return this;
    }

    public ValueType<T> build() {
      return new ValueType<>(this);
    }
  }
}
