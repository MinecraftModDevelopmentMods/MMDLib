package zone.moddev.mc.lib.util.parse.value;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** A JSON number read as an {@code int}. */
public interface IntValue {
  /**
   * Passes the value to {@code value}; other exceptions it throws are wrapped in a {@link
   * zone.moddev.mc.lib.util.parse.JParseException} naming this value.
   */
  void handle(IntConsumer value);

  /** Throws if the value is below {@code min}. */
  IntValue min(int min);

  /** Throws unless {@code min <= value < maxExclusive}. */
  IntValue range(int min, int maxExclusive);

  /** The value. */
  int getAsInt();

  /** Maps the value. */
  default <T> MappedValue<T> map(IntFunction<T> mapping) {
    return MappedValue.of(mapping.apply(getAsInt()));
  }
}
