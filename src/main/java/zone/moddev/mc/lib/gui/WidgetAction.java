package zone.moddev.mc.lib.gui;

import java.util.Objects;
import java.util.function.BiPredicate;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import io.netty.buffer.Unpooled;

import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;

/**
 * A typed action allowed by a widget type. Codecs must bound strings/collections before allocating.
 * The server handler must validate permissions and data before mutating, returning false to reject.
 * Register the same ID and codec on both sides; the handler only runs on the server thread.
 */
public record WidgetAction<T>(
    ResourceLocation id,
    StreamCodec<RegistryFriendlyByteBuf, T> codec,
    int maxBytes,
    BiPredicate<Context, T> handler) {
  public static final int MAX_BYTES = 4096;

  public WidgetAction {
    Objects.requireNonNull(id);
    Objects.requireNonNull(codec);
    Objects.requireNonNull(handler);
    if (maxBytes < 1 || maxBytes > MAX_BYTES)
      throw new IllegalArgumentException("Widget action limit must be 1..4096 bytes");
  }

  public record Context(ServerPlayer player, MachineMenu menu, Element.Custom widget) {}

  /** Encodes inside a bounded buffer, before any packet is sent. */
  public byte[] encode(RegistryAccess registries, T value) {
    var buffer =
        new RegistryFriendlyByteBuf(Unpooled.buffer(Math.min(64, maxBytes), maxBytes), registries);
    try {
      codec.encode(buffer, value);
      byte[] data = new byte[buffer.readableBytes()];
      buffer.readBytes(data);
      return data;
    } finally {
      buffer.release();
    }
  }

  /** Decodes exactly one action, rejecting trailing data as well as oversized payloads. */
  public T decode(RegistryAccess registries, byte[] data) {
    if (data.length > maxBytes) throw new IllegalArgumentException("Widget action is too large");
    var buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), registries);
    try {
      T value = Objects.requireNonNull(codec.decode(buffer));
      if (buffer.isReadable()) throw new IllegalArgumentException("Trailing widget action data");
      return value;
    } finally {
      buffer.release();
    }
  }

  public boolean dispatch(Context context, byte[] data) {
    T value;
    try {
      value = decode(context.player().registryAccess(), data);
    } catch (RuntimeException malformed) {
      return false;
    }
    return handler.test(context, value);
  }
}
