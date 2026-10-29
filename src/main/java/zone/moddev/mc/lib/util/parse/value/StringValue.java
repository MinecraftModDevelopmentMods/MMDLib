package zone.moddev.mc.lib.util.parse.value;

import java.util.function.Consumer;

import zone.moddev.mc.lib.util.parse.function.StringFunction;

/** A JSON string. */
public interface StringValue {
  void handle(Consumer<String> value);

  String getAsString();

  default <T> MappedValue<T> map(StringFunction<T> mapping) {
    return MappedValue.of(mapping.apply(getAsString()));
  }
}
