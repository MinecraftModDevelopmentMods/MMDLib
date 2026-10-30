package zone.moddev.mc.lib.util.parse.value;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Stream;

import zone.moddev.mc.lib.util.parse.function.IntObjBiConsumer;
import zone.moddev.mc.lib.util.parse.function.MappedArrayValueFunction;

/** The items of a JSON array after mapping, with the same size checks as {@link ArrayValue}. */
public interface MappedArrayValue<V> {
  /** Visits every item with its index. */
  void forEach(IntObjBiConsumer<V> visitor);

  /** Passes a stream of the items to {@code collector}. */
  void collect(Consumer<Stream<V>> collector);

  /** Maps every item now, keeping the results for further checks. */
  <T> MappedArrayValue<T> map(Function<V, T> mapping);

  /** Reduces the stream of items to a single result. */
  <T> T flatMap(Function<Stream<V>, T> collector);

  /** Throws if there are no items. */
  MappedArrayValue<V> notEmpty();

  /** Throws if there are fewer than {@code min} items. */
  MappedArrayValue<V> atLeast(int min);

  /** Throws unless there are from {@code min} to {@code maxExclusive - 1} items. */
  MappedArrayValue<V> between(int min, int maxExclusive);

  /** Maps every item into a new array made by {@code factory}. */
  <T> MappedValue<T[]> flatten(Function<V, T> mapping, IntFunction<T[]> factory);

  /** The items as a new array made by {@code factory}. */
  default MappedValue<V[]> flatten(IntFunction<V[]> factory) {
    return flatten(Function.identity(), factory);
  }

  /** Maps the items as a whole. */
  default <T> MappedValue<T> mapWhole(MappedArrayValueFunction<V, T> mapping) {
    return MappedValue.of(mapping.apply(this));
  }
}
