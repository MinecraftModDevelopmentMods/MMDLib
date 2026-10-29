package zone.moddev.mc.lib.util.parse.function;

import java.util.function.Function;

import zone.moddev.mc.lib.util.parse.value.ArrayValue;

/** Maps a whole JSON array. */
@FunctionalInterface
public interface ArrayValueFunction<T> extends Function<ArrayValue, T> {}
