package zone.moddev.mc.lib.util.parse.function;

/** Maps an array index and the item at it. */
@FunctionalInterface
public interface IntObjBiFunction<T, R> {
  R apply(int index, T obj);
}
