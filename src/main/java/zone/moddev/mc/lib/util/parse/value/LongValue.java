package zone.moddev.mc.lib.util.parse.value;

import java.util.function.LongConsumer;
import java.util.function.LongFunction;

/** A JSON number read as a {@code long}. */
public interface LongValue {
  /**
   * Passes the value to {@code value}; other exceptions it throws are wrapped in a {@link
   * zone.moddev.mc.lib.util.parse.JParseException} naming this value.
   */
  void handle(LongConsumer value);

  /** Throws if the value is below {@code min}. */
  LongValue min(long min);

  /** Throws unless {@code min <= value < maxExclusive}. */
  LongValue range(long min, long maxExclusive);

  /** The value. */
  long getAsLong();

  /** Maps the value. */
  default <T> MappedValue<T> map(LongFunction<T> mapping) {
    return MappedValue.of(mapping.apply(getAsLong()));
  }
}
