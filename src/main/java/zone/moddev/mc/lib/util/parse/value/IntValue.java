package zone.moddev.mc.lib.util.parse.value;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** A JSON number read as an {@code int}. */
public interface IntValue {
  void handle(IntConsumer value);

  /** Throws if the value is below {@code min}. */
  IntValue min(int min);

  /** Throws unless {@code min <= value < maxExclusive}. */
  IntValue range(int min, int maxExclusive);

  int getAsInt();

  default <T> MappedValue<T> map(IntFunction<T> mapping) {
    return MappedValue.of(mapping.apply(getAsInt()));
  }
}
