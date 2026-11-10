package zone.moddev.mc.lib.gui.runtime.component;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.netty.buffer.Unpooled;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.sync.ElementStateValue;
import zone.moddev.mc.lib.gui.sync.ElementSync;

/**
 * Registered component-owned field. Bump version when changing its wire codec
 * or meaning.
 */
public record ComponentSync<T>(ResourceLocation id, int version,
    StreamCodec<RegistryFriendlyByteBuf, T> codec, int maxBytes,
    Supplier<T> initial, Predicate<T> valid) {
    public ComponentSync {
        Objects.requireNonNull(id);
        Objects.requireNonNull(codec);
        Objects.requireNonNull(initial);
        Objects.requireNonNull(valid);
        if (version < 1 || maxBytes < 1 || maxBytes > ElementSync.MAX_BYTES)
            throw new IllegalArgumentException(
                "Invalid component field limits: " + id);
    }

    public ComponentSync(ResourceLocation id, int version,
        StreamCodec<RegistryFriendlyByteBuf, T> codec, int maxBytes,
        Supplier<T> initial) {
        this(id, version, codec, maxBytes, initial, value -> true);
    }

    private T checked(T value) {
        if (value == null || !valid.test(value))
            throw new IllegalArgumentException(
                "Invalid component state: " + id);
        return value;
    }

    public ElementStateValue encode(RegistryAccess registries, T value) {
        checked(value);
        var buffer = new RegistryFriendlyByteBuf(
            Unpooled.buffer(Math.min(64, maxBytes), maxBytes), registries);
        try {
            codec.encode(buffer, value);
            var bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return new ElementStateValue(bytes);
        } finally {
            buffer.release();
        }
    }

    public T decode(RegistryAccess registries, ElementStateValue value) {
        if (value.size() > maxBytes)
            throw new IllegalArgumentException(
                "Component state exceeds limit: " + id);
        var buffer = new RegistryFriendlyByteBuf(
            Unpooled.wrappedBuffer(value.data()), registries);
        try {
            var result = checked(codec.decode(buffer));
            if (buffer.isReadable())
                throw new IllegalArgumentException(
                    "Trailing component state data: " + id);
            return result;
        } finally {
            buffer.release();
        }
    }

    public String fingerprint() {
        return id + "/" + version + "/" + maxBytes;
    }
}
