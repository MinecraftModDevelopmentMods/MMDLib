package zone.moddev.mc.lib.gui.sync;

import java.util.Objects;
import java.util.function.*;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

import io.netty.buffer.Unpooled;

import zone.moddev.mc.lib.gui.model.Element;

/** One element-owned, bounded typed state field. Keep declarations stable on both sides. */
public final class ElementSync<T> {
  public static final int MAX_BYTES = 65536;

  public record Source(Element element, BlockEntity machine, Player viewer) {}

  private final ResourceLocation id;
  private final StreamCodec<RegistryFriendlyByteBuf, T> codec;
  private final int maxBytes;
  private final Function<Element, T> initial;
  private final BiFunction<Source, T, T> sample;
  private final BiPredicate<Element, T> valid;
  private final boolean layout;

  private ElementSync(Builder<T> builder) {
    id = builder.id;
    codec = builder.codec;
    maxBytes = builder.maxBytes;
    initial = builder.initial;
    sample = builder.sample;
    valid = builder.valid;
    layout = builder.layout;
  }

  public ResourceLocation id() {
    return id;
  }

  public int maxBytes() {
    return maxBytes;
  }

  public boolean affectsLayout() {
    return layout;
  }

  public T initial(Element element) {
    return checked(element, initial.apply(element));
  }

  T sample(Source source, T previous) {
    return checked(source.element(), sample.apply(source, previous));
  }

  private T checked(Element element, T value) {
    if (value == null || !valid.test(element, value))
      throw new IllegalArgumentException("Invalid element state: " + id);
    return value;
  }

  public ElementStateValue encode(RegistryAccess registries, Element element, T value) {
    checked(element, value);
    var buffer =
        new RegistryFriendlyByteBuf(Unpooled.buffer(Math.min(64, maxBytes), maxBytes), registries);
    try {
      codec.encode(buffer, value);
      var bytes = new byte[buffer.readableBytes()];
      buffer.readBytes(bytes);
      return new ElementStateValue(bytes);
    } finally {
      buffer.release();
    }
  }

  public T decode(RegistryAccess registries, Element element, ElementStateValue encoded) {
    if (encoded.size() > maxBytes)
      throw new IllegalArgumentException("Element state exceeds limit: " + id);
    var buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(encoded.data()), registries);
    try {
      var value = checked(element, codec.decode(buffer));
      if (buffer.isReadable())
        throw new IllegalArgumentException("Trailing element state data: " + id);
      return value;
    } finally {
      buffer.release();
    }
  }

  public static <T> Builder<T> field(
      ResourceLocation id,
      StreamCodec<RegistryFriendlyByteBuf, T> codec,
      int maxBytes,
      Function<Element, T> initial) {
    return new Builder<>(id, codec, maxBytes, initial);
  }

  public static final class Builder<T> {
    private final ResourceLocation id;
    private final StreamCodec<RegistryFriendlyByteBuf, T> codec;
    private final int maxBytes;
    private final Function<Element, T> initial;
    private BiFunction<Source, T, T> sample = (source, previous) -> previous;
    private BiPredicate<Element, T> valid = (element, value) -> true;
    private boolean layout;

    private Builder(
        ResourceLocation id,
        StreamCodec<RegistryFriendlyByteBuf, T> codec,
        int maxBytes,
        Function<Element, T> initial) {
      this.id = Objects.requireNonNull(id);
      this.codec = Objects.requireNonNull(codec);
      this.initial = Objects.requireNonNull(initial);
      if (maxBytes < 1 || maxBytes > MAX_BYTES)
        throw new IllegalArgumentException("Element state limit must be 1..65536 bytes");
      this.maxBytes = maxBytes;
    }

    /** Invoked only by the server; the default preserves per-menu state set by an action. */
    public Builder<T> sample(BiFunction<Source, T, T> sampler) {
      sample = Objects.requireNonNull(sampler);
      return this;
    }

    public Builder<T> validate(BiPredicate<Element, T> validator) {
      valid = Objects.requireNonNull(validator);
      return this;
    }

    public Builder<T> affectsLayout() {
      layout = true;
      return this;
    }

    public ElementSync<T> build() {
      return new ElementSync<>(this);
    }
  }
}
