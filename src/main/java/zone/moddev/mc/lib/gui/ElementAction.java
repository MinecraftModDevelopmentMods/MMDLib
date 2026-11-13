package zone.moddev.mc.lib.gui;

import java.util.Objects;
import java.util.function.BiPredicate;

import io.netty.buffer.Unpooled;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.runtime.MachineMenu;

/**
 * A bounded typed action declared by an element. Authorization precedes
 * decoding.
 */
public class ElementAction<T> {
    public static final int MAX_BYTES = 4096;
    private final ResourceLocation id;
    private final StreamCodec<RegistryFriendlyByteBuf, T> codec;
    private final int maxBytes;
    private final BiPredicate<Context, T> handler;

    public ElementAction(ResourceLocation id,
        StreamCodec<RegistryFriendlyByteBuf, T> codec, int maxBytes,
        BiPredicate<Context, T> handler) {
        this.id = Objects.requireNonNull(id);
        this.codec = Objects.requireNonNull(codec);
        this.handler = Objects.requireNonNull(handler);
        if (maxBytes < 1 || maxBytes > MAX_BYTES)
            throw new IllegalArgumentException(
                "Element action limit must be 1..4096 bytes");
        this.maxBytes = maxBytes;
    }

    public final ResourceLocation id() {
        return id;
    }

    public final StreamCodec<RegistryFriendlyByteBuf, T> codec() {
        return codec;
    }

    public final int maxBytes() {
        return maxBytes;
    }

    public record Context(ServerPlayer player, MachineMenu menu,
        Element element) {
    }

    /**
     * Executes an already validated local payload; the caller must authorize
     * the context first.
     */
    public boolean execute(Context context, T value) {
        if (context
            .element() instanceof zone.moddev.mc.lib.gui.model.elements.ButtonControl button
            && !button.serverEnabled(context
                .menu().blockEntity instanceof zone.moddev.mc.lib.gui.runtime.ScreenHost host
                    ? host
                    : null,
                context.player()))
            return false;
        return handler.test(context, value);
    }

    /** Encodes inside a bounded buffer, before any packet is sent. */
    public byte[] encode(RegistryAccess registries, T value) {
        var buffer = new RegistryFriendlyByteBuf(
            Unpooled.buffer(Math.min(64, maxBytes), maxBytes), registries);
        try {
            codec.encode(buffer, value);
            byte[] data = new byte[buffer.readableBytes()];
            buffer.readBytes(data);
            return data;
        } finally {
            buffer.release();
        }
    }

    /**
     * Decodes exactly one action, rejecting trailing data as well as oversized
     * payloads.
     */
    public T decode(RegistryAccess registries, byte[] data) {
        if (data.length > maxBytes)
            throw new IllegalArgumentException("Element action is too large");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data),
            registries);
        try {
            T value = Objects.requireNonNull(codec.decode(buffer));
            if (buffer.isReadable())
                throw new IllegalArgumentException(
                    "Trailing element action data");
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
        return execute(context, value);
    }
}
