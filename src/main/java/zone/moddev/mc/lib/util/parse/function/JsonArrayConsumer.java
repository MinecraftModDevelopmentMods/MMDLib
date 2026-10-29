package zone.moddev.mc.lib.util.parse.function;

import java.util.function.Consumer;

import com.google.gson.JsonArray;

/** Accepts a raw Gson array. */
@FunctionalInterface
public interface JsonArrayConsumer extends Consumer<JsonArray> {}
