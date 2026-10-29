package zone.moddev.mc.lib.util.parse.function;

/** Maps a {@code boolean} without boxing. */
@FunctionalInterface
public interface BooleanFunction<T> {
  T apply(boolean b);
}
