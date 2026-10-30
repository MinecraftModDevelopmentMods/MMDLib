package zone.moddev.mc.lib.common.codecs;

import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/** Canonical 'event' with backwards-compatible 'events' input. */
public final class EventBindings {
  private EventBindings() {}

  private static final Codec<Map<String, ResourceLocation>> VALUES =
      Codec.unboundedMap(
          Codec.STRING.validate(
              name ->
                  name.isBlank()
                      ? DataResult.error(() -> "Blank event name")
                      : DataResult.success(name)),
          ResourceLocation.CODEC);

  private record Fields(
      Optional<Map<String, ResourceLocation>> event,
      Optional<Map<String, ResourceLocation>> events) {}

  private static final MapCodec<Fields> FIELDS =
      RecordCodecBuilder.mapCodec(
          i ->
              i.group(
                      VALUES.optionalFieldOf("event").forGetter(Fields::event),
                      VALUES.optionalFieldOf("events").forGetter(Fields::events))
                  .apply(i, Fields::new));

  /**
   * Reads the bindings from either {@code event} or {@code events} (not both), as a map from event
   * name to handler ID; missing means none. Writes {@code event}, omitted when there are none.
   */
  public static final MapCodec<Map<String, ResourceLocation>> MAP_CODEC =
      FIELDS.flatXmap(
          fields -> {
            if (fields.event.isPresent() && fields.events.isPresent())
              return DataResult.error(() -> "Use event or events, not both");
            return DataResult.success(fields.event.orElseGet(() -> fields.events.orElse(Map.of())));
          },
          value ->
              DataResult.success(
                  new Fields(
                      value.isEmpty() ? Optional.empty() : Optional.of(value), Optional.empty())));
}
