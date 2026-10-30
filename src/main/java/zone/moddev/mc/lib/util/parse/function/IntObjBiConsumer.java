package zone.moddev.mc.lib.util.parse.function;

/** Accepts an array index and the item at it. */
@FunctionalInterface
public interface IntObjBiConsumer<T> {
  /** Accepts the item {@code obj} at {@code index}. */
  void accept(int index, T obj);
}
