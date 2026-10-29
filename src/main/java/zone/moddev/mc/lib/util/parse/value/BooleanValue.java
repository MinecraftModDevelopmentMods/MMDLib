package zone.moddev.mc.lib.util.parse.value;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;

import zone.moddev.mc.lib.util.parse.function.BooleanFunction;

/** A JSON boolean. */
public interface BooleanValue {
  void handle(BooleanConsumer value);

  boolean getAsBoolean();

  default <T> MappedValue<T> map(BooleanFunction<T> mapping) {
    return MappedValue.of(mapping.apply(getAsBoolean()));
  }
}
