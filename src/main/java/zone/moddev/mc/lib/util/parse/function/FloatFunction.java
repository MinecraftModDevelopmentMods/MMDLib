package zone.moddev.mc.lib.util.parse.function;

/** Maps a {@code float} without boxing. */
@FunctionalInterface
public interface FloatFunction<T> {
  /** Maps {@code value}. */
  T apply(float value);
}
