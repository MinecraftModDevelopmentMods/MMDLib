package zone.moddev.mc.lib.util.parse.value;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;

import zone.moddev.mc.lib.util.parse.function.BooleanFunction;

/** A JSON boolean. */
public interface BooleanValue {
  /**
   * Passes the value to {@code value}; other exceptions it throws are wrapped in a {@link
   * zone.moddev.mc.lib.util.parse.JParseException} naming this value.
   */
  void handle(BooleanConsumer value);

  /** The value. */
  boolean getAsBoolean();

  /** Maps the value. */
  default <T> MappedValue<T> map(BooleanFunction<T> mapping) {
    return MappedValue.of(mapping.apply(getAsBoolean()));
  }
}
