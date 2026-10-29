package zone.moddev.mc.lib.util.parse.value;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/** A JSON number read as a {@code double}. */
public interface DoubleValue {
  void handle(DoubleConsumer value);

  DoubleValue min(double min);

  DoubleValue range(double min, double maxExclusive);

  double getAsDouble();

  default <T> MappedValue<T> map(DoubleFunction<T> mapping) {
    return MappedValue.of(mapping.apply(getAsDouble()));
  }
}
