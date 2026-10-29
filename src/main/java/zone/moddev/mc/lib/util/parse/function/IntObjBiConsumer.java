package zone.moddev.mc.lib.util.parse.function;

/** Accepts an array index and the item at it. */
@FunctionalInterface
public interface IntObjBiConsumer<T> {
  void accept(int index, T obj);
}
