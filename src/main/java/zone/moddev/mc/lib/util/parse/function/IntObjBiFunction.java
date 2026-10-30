package zone.moddev.mc.lib.util.parse.function;

/** Maps an array index and the item at it. */
@FunctionalInterface
public interface IntObjBiFunction<T, R> {
  /** Maps the item {@code obj} at {@code index}. */
  R apply(int index, T obj);
}
