package zone.moddev.mc.lib.util.parse.function;

import java.util.function.Function;

import zone.moddev.mc.lib.util.parse.value.ObjValue;

/** Maps a whole JSON object. */
@FunctionalInterface
public interface ObjValueFunction<T> extends Function<ObjValue, T> {}
