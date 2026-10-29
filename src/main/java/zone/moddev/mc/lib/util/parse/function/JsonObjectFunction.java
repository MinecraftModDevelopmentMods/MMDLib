package zone.moddev.mc.lib.util.parse.function;

import java.util.function.Function;

import com.google.gson.JsonObject;

/** Maps a raw Gson object. */
@FunctionalInterface
public interface JsonObjectFunction<T> extends Function<JsonObject, T> {}
