package zone.moddev.mc.lib.api.automation;

import java.util.*;
import java.util.function.BiPredicate;

import com.google.gson.JsonParser;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Common registration for an item-bound custom leaf. The codec and handler
 * validate one bounded action against a candidate configuration, which the
 * filter validates again before commit.
 */
public record FilterWidgetType<T>(ResourceLocation id, Codec<T> action,
    BiPredicate<CompoundTag, T> apply) {
    private static final Map<ResourceLocation, FilterWidgetType<?>> TYPES = new HashMap<>();

    public FilterWidgetType {
        Objects.requireNonNull(id);
        Objects.requireNonNull(action);
        Objects.requireNonNull(apply);
    }

    public static synchronized <T> FilterWidgetType<T> register(
        ResourceLocation id, Codec<T> codec,
        BiPredicate<CompoundTag, T> apply) {
        var type = new FilterWidgetType<>(id, codec, apply);
        if (TYPES.putIfAbsent(id, type) != null)
            throw new IllegalArgumentException(
                "Duplicate filter widget: " + id);
        return type;
    }

    public String encode(T value) {
        String encoded = action.encodeStart(JsonOps.INSTANCE, value)
            .getOrThrow().toString();
        if (encoded.length() > 256)
            throw new IllegalArgumentException("Filter action too large");
        return encoded;
    }

    public boolean dispatch(CompoundTag candidate, String encoded) {
        if (encoded.length() > 256)
            return false;
        try {
            var decoded = action
                .parse(JsonOps.INSTANCE, JsonParser.parseString(encoded))
                .result();
            return decoded.isPresent() && apply.test(candidate, decoded.get());
        } catch (RuntimeException malformed) {
            return false;
        }
    }
}
