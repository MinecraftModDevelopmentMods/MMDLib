package zone.moddev.mc.lib.util.parse.function;

import java.util.function.Consumer;

import com.google.gson.JsonElement;

/** Accepts a raw Gson element. */
@FunctionalInterface
public interface JsonElementConsumer extends Consumer<JsonElement> {}
