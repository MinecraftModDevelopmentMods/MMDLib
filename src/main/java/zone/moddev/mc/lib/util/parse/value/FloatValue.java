package zone.moddev.mc.lib.util.parse.value;

import it.unimi.dsi.fastutil.floats.FloatConsumer;

import zone.moddev.mc.lib.util.parse.function.FloatFunction;

/** A JSON number read as a {@code float}. */
public interface FloatValue {
  void handle(FloatConsumer value);

  FloatValue min(float min);

  FloatValue range(float min, float maxExclusive);

  float getAsFloat();

  default <T> MappedValue<T> map(FloatFunction<T> mapping) {
    return MappedValue.of(mapping.apply(getAsFloat()));
  }
}
