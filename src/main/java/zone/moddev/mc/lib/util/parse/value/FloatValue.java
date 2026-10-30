package zone.moddev.mc.lib.util.parse.value;

import it.unimi.dsi.fastutil.floats.FloatConsumer;

import zone.moddev.mc.lib.util.parse.function.FloatFunction;

/** A JSON number read as a {@code float}. */
public interface FloatValue {
  /** Passes the value to {@code value}. */
  void handle(FloatConsumer value);

  /** Throws if the value is below {@code min}. */
  FloatValue min(float min);

  /** Throws unless {@code min <= value < maxExclusive}. */
  FloatValue range(float min, float maxExclusive);

  /** The value. */
  float getAsFloat();

  /** Maps the value. */
  default <T> MappedValue<T> map(FloatFunction<T> mapping) {
    return MappedValue.of(mapping.apply(getAsFloat()));
  }
}
