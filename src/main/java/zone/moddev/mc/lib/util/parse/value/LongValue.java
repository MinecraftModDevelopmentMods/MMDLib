package zone.moddev.mc.lib.util.parse.value;

import java.util.function.LongConsumer;
import java.util.function.LongFunction;

/** A JSON number read as a {@code long}. */
public interface LongValue {
  void handle(LongConsumer value);

  LongValue min(long min);

  LongValue range(long min, long maxExclusive);

  long getAsLong();

  default <T> MappedValue<T> map(LongFunction<T> mapping) {
    return MappedValue.of(mapping.apply(getAsLong()));
  }
}
