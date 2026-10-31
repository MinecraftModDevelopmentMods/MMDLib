package zone.moddev.mc.lib.machine.internals;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A named place for parts inside a machine, such as {@code gears}. JSON: {@code {"count": 2,
 * "accepts": "#c:gears", "optional": false}}; {@code count} defaults to 1, {@code accepts} to any
 * part made for this socket.
 *
 * @param count how many parts it holds, 1 to 64
 * @param accepts which items it takes, on top of the part file naming this socket
 * @param optional whether the machine runs with positions left empty
 */
public record SocketDefinition(int count, Optional<ItemMatch> accepts, boolean optional) {
  /** The JSON form. */
  public static final Codec<SocketDefinition> CODEC =
      RecordCodecBuilder.create(
          instance ->
              instance
                  .group(
                      Codec.intRange(1, 64)
                          .optionalFieldOf("count", 1)
                          .forGetter(SocketDefinition::count),
                      ItemMatch.CODEC
                          .optionalFieldOf("accepts")
                          .forGetter(SocketDefinition::accepts),
                      Codec.BOOL
                          .optionalFieldOf("optional", false)
                          .forGetter(SocketDefinition::optional))
                  .apply(instance, SocketDefinition::new));
}
