package zone.moddev.mc.lib.util.parse.function;

import java.util.function.Function;

/** Maps a string. */
@FunctionalInterface
public interface StringFunction<T> extends Function<String, T> {}
