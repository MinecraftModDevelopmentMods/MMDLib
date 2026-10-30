package zone.moddev.mc.lib.util.parse.value;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/** A JSON number read as a {@code double}. */
public interface DoubleValue {
  /**
   * Passes the value to {@code value}; other exceptions it throws are wrapped in a {@link
   * zone.moddev.mc.lib.util.parse.JParseException} naming this value.
   */
  void handle(DoubleConsumer value);

  /** Throws if the value is below {@code min}. */
  DoubleValue min(double min);

  /** Throws unless {@code min <= value < maxExclusive}. */
  DoubleValue range(double min, double maxExclusive);

  /** The value. */
  double getAsDouble();

  /** Maps the value. */
  default <T> MappedValue<T> map(DoubleFunction<T> mapping) {
    return MappedValue.of(mapping.apply(getAsDouble()));
  }
}
