package zone.moddev.mc.lib.util.parse.function;

import java.util.function.Consumer;

import com.google.gson.JsonObject;

/** Accepts a raw Gson object. */
@FunctionalInterface
public interface JsonObjectConsumer extends Consumer<JsonObject> {}
