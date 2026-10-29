package zone.moddev.mc.lib.util.parse.function;

import java.util.function.Function;

import com.google.gson.JsonElement;

/** Maps a raw Gson element. */
@FunctionalInterface
public interface JsonElementFunction<T> extends Function<JsonElement, T> {}
