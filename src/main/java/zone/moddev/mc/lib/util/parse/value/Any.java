package zone.moddev.mc.lib.util.parse.value;

import java.util.function.Consumer;

import com.google.gson.JsonElement;

import zone.moddev.mc.lib.util.parse.function.AnyFunction;
import zone.moddev.mc.lib.util.parse.function.JsonElementConsumer;
import zone.moddev.mc.lib.util.parse.function.JsonElementFunction;

/**
 * A JSON value of not-yet-known type. Either assert its type ({@link #obj()}, {@link #string()},
 * ...), which throws when it differs, or branch with the {@code if...} methods and finish with
 * {@link #typeError()}.
 */
public interface Any {
  /**
   * This value as an object.
   *
   * @throws zone.moddev.mc.lib.util.parse.JParseException if it is not one
   */
  ObjValue obj();

  /**
   * This value as an array.
   *
   * @throws zone.moddev.mc.lib.util.parse.JParseException if it is not one
   */
  ArrayValue array();

  /**
   * This value as a string.
   *
   * @throws zone.moddev.mc.lib.util.parse.JParseException if it is not one
   */
  StringValue string();

  /**
   * This value as an {@code int}.
   *
   * @throws zone.moddev.mc.lib.util.parse.JParseException if it is not a whole number in range
   */
  IntValue intValue();

  /**
   * This value as a {@code long}.
   *
   * @throws zone.moddev.mc.lib.util.parse.JParseException if it is not a whole number in range
   */
  LongValue longValue();

  /**
   * This value as a {@code float}.
   *
   * @throws zone.moddev.mc.lib.util.parse.JParseException if it is not a number
   */
  FloatValue floatValue();

  /**
   * This value as a {@code double}.
   *
   * @throws zone.moddev.mc.lib.util.parse.JParseException if it is not a number
   */
  DoubleValue doubleValue();

  /**
   * This value as a boolean.
   *
   * @throws zone.moddev.mc.lib.util.parse.JParseException if it is not one
   */
  BooleanValue bool();

  /** Runs {@code visitor} if this value is an object. */
  Any ifObj(Consumer<ObjValue> visitor);

  /** Runs {@code visitor} if this value is an array. */
  Any ifArray(Consumer<ArrayValue> visitor);

  /** Runs {@code visitor} if this value is a string. */
  Any ifString(Consumer<StringValue> visitor);

  /** Runs {@code visitor} if this value is an {@code int}. */
  Any ifInteger(Consumer<IntValue> visitor);

  /** Runs {@code visitor} if this value is a {@code long}. */
  Any ifLong(Consumer<LongValue> visitor);

  /** Runs {@code visitor} if this value is a {@code float}. */
  Any ifFloat(Consumer<FloatValue> visitor);

  /** Runs {@code visitor} if this value is a {@code double}. */
  Any ifDouble(Consumer<DoubleValue> visitor);

  /** Runs {@code visitor} if this value is a boolean. */
  Any ifBool(Consumer<BooleanValue> visitor);

  /**
   * Ends a chain of {@code if...} calls: throws, listing the accepted types, when none of them
   * matched.
   */
  void typeError();

  /** Passes the raw Gson element to {@code visitor}. */
  void raw(JsonElementConsumer visitor);

  /** The raw Gson element. */
  JsonElement get();

  default <T> MappedValue<T> map(JsonElementFunction<T> mapping) {
    return MappedValue.of(mapping.apply(get()));
  }

  default <T> MappedValue<T> map(AnyFunction<T> mapping) {
    return MappedValue.of(mapping.apply(this));
  }
}
