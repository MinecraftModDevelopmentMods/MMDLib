package zone.moddev.mc.lib.common.codecs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.Optional;

/** Canonical 'event' with backwards-compatible 'events' input. */
public final class EventBindings {
    private EventBindings() {}
    private static final Codec<Map<String, ResourceLocation>> VALUES = Codec.unboundedMap(Codec.STRING.validate(name ->
            name.isBlank() ? DataResult.error(() -> "Blank event name") : DataResult.success(name)), ResourceLocation.CODEC);
    private record Fields(Optional<Map<String, ResourceLocation>> event, Optional<Map<String, ResourceLocation>> events) {}
    private static final MapCodec<Fields> FIELDS = RecordCodecBuilder.mapCodec(i -> i.group(
            VALUES.optionalFieldOf("event").forGetter(Fields::event), VALUES.optionalFieldOf("events").forGetter(Fields::events)
    ).apply(i, Fields::new));
    public static final MapCodec<Map<String, ResourceLocation>> MAP_CODEC = FIELDS.flatXmap(fields -> {
        if (fields.event.isPresent() && fields.events.isPresent()) return DataResult.error(() -> "Use event or events, not both");
        return DataResult.success(fields.event.orElseGet(() -> fields.events.orElse(Map.of())));
    }, value -> DataResult.success(new Fields(value.isEmpty() ? Optional.empty() : Optional.of(value), Optional.empty())));
}
