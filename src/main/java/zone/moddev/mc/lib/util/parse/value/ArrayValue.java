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

  void collect(Consumer<Stream<Any>> collector);

  <T> MappedArrayValue<T> map(Function<Any, T> mapping);

  <T> T flatMap(Function<Stream<Any>, T> collector);

  <T> MappedValue<T[]> flatten(Function<Any, T> mapping, IntFunction<T[]> factory);

  /** Throws if the array is empty. */
  ArrayValue notEmpty();

  /** Throws if the array has fewer than {@code min} items. */
  ArrayValue atLeast(int min);

  /** Throws unless the array has from {@code min} to {@code maxExclusive - 1} items. */
  ArrayValue between(int min, int maxExclusive);

  /** The raw Gson array. */
  JsonArray getAsJsonArray();

  void raw(JsonArrayConsumer value);

  default <T> MappedValue<T> unwrapRaw(JsonArrayFunction<T> mapping) {
    return MappedValue.of(mapping.apply(getAsJsonArray()));
  }

  default <T> MappedValue<T> mapWhole(ArrayValueFunction<T> mapping) {
    return MappedValue.of(mapping.apply(this));
  }

  default MappedArrayValue<IntValue> ints() {
    return this.map(Any::intValue);
  }

  default MappedArrayValue<LongValue> longs() {
    return this.map(Any::longValue);
  }

  default MappedArrayValue<FloatValue> floats() {
    return this.map(Any::floatValue);
  }

  default MappedArrayValue<DoubleValue> doubles() {
    return this.map(Any::doubleValue);
  }

  default MappedArrayValue<BooleanValue> booleans() {
    return this.map(Any::bool);
  }

  default MappedArrayValue<StringValue> strings() {
    return this.map(Any::string);
  }
}
