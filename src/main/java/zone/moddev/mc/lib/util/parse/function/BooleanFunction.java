package zone.moddev.mc.lib.util.parse.function;

/** Maps a {@code boolean} without boxing. */
@FunctionalInterface
public interface BooleanFunction<T> {
  /** Maps {@code b}. */
  T apply(boolean b);
}
