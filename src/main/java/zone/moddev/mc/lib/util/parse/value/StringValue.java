package zone.moddev.mc.lib.util.parse.value;

import java.util.function.Consumer;

import zone.moddev.mc.lib.util.parse.function.StringFunction;

/** A JSON string. */
public interface StringValue {
  /**
   * Passes the value to {@code value}; other exceptions it throws are wrapped in a {@link
   * zone.moddev.mc.lib.util.parse.JParseException} naming this value.
   */
  void handle(Consumer<String> value);

  /** The value. */
  String getAsString();

  /** Maps the value. */
  default <T> MappedValue<T> map(StringFunction<T> mapping) {
    return MappedValue.of(mapping.apply(getAsString()));
  }
}
