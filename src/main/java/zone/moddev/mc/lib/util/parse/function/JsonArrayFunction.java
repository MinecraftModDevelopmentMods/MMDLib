package zone.moddev.mc.lib.util.parse.function;

import java.util.function.Function;

import com.google.gson.JsonArray;

/** Maps a raw Gson array. */
@FunctionalInterface
public interface JsonArrayFunction<T> extends Function<JsonArray, T> {}
