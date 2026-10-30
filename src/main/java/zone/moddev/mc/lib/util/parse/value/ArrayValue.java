package zone.moddev.mc.lib.util.parse.value;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Stream;

import com.google.gson.JsonArray;

import zone.moddev.mc.lib.util.parse.function.ArrayValueFunction;
import zone.moddev.mc.lib.util.parse.function.IntObjBiConsumer;
import zone.moddev.mc.lib.util.parse.function.JsonArrayConsumer;
import zone.moddev.mc.lib.util.parse.function.JsonArrayFunction;

/** A JSON array being walked. */
public interface ArrayValue {
  /** Visits every item with its index. */
  void forEach(IntObjBiConsumer<Any> visitor);

  /** Passes a stream of the items to {@code collector}. */
  void collect(Consumer<Stream<Any>> collector);

  /** Maps every item now, keeping the results for further checks. */
  <T> MappedArrayValue<T> map(Function<Any, T> mapping);

  /** Reduces the stream of items to a single result. */
  <T> T flatMap(Function<Stream<Any>, T> collector);

  /** Maps every item into a new array made by {@code factory}. */
  <T> MappedValue<T[]> flatten(Function<Any, T> mapping, IntFunction<T[]> factory);

  /** Throws if the array is empty. */
  ArrayValue notEmpty();

  /** Throws if the array has fewer than {@code min} items. */
  ArrayValue atLeast(int min);

  /** Throws unless the array has from {@code min} to {@code maxExclusive - 1} items. */
  ArrayValue between(int min, int maxExclusive);

  /** The raw Gson array. */
  JsonArray getAsJsonArray();

  /**
   * Passes the raw Gson array to {@code value}; other exceptions it throws are wrapped in a {@link
   * zone.moddev.mc.lib.util.parse.JParseException} naming this array.
   */
  void raw(JsonArrayConsumer value);

  /** Maps the raw Gson array. */
  default <T> MappedValue<T> unwrapRaw(JsonArrayFunction<T> mapping) {
    return MappedValue.of(mapping.apply(getAsJsonArray()));
  }

  /** Maps this array as a whole. */
  default <T> MappedValue<T> mapWhole(ArrayValueFunction<T> mapping) {
    return MappedValue.of(mapping.apply(this));
  }

  /** Every item as an {@code int}; throws on the first that is not one. */
  default MappedArrayValue<IntValue> ints() {
    return this.map(Any::intValue);
  }

  /** Every item as a {@code long}; throws on the first that is not one. */
  default MappedArrayValue<LongValue> longs() {
    return this.map(Any::longValue);
  }

  /** Every item as a {@code float}; throws on the first that is not a number. */
  default MappedArrayValue<FloatValue> floats() {
    return this.map(Any::floatValue);
  }

  /** Every item as a {@code double}; throws on the first that is not a number. */
  default MappedArrayValue<DoubleValue> doubles() {
    return this.map(Any::doubleValue);
  }

  /** Every item as a boolean; throws on the first that is not one. */
  default MappedArrayValue<BooleanValue> booleans() {
    return this.map(Any::bool);
  }

  /** Every item as a string; throws on the first that is not one. */
  default MappedArrayValue<StringValue> strings() {
    return this.map(Any::string);
  }
}
