package zone.moddev.mc.lib.util.parse.value;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.google.gson.JsonObject;

import zone.moddev.mc.lib.util.parse.function.JsonObjectConsumer;
import zone.moddev.mc.lib.util.parse.function.JsonObjectFunction;
import zone.moddev.mc.lib.util.parse.function.ObjValueFunction;

/** A JSON object being walked. */
public interface ObjValue {
  /**
   * Visits the required key {@code keyName}.
   *
   * @throws zone.moddev.mc.lib.util.parse.JParseException if it is missing
   */
  ObjValue key(String keyName, Consumer<Any> visitor);

  /** Visits {@code keyName} if present. */
  ObjValue ifKey(String keyName, Consumer<Any> visitor);

  /** Visits every key and value. */
  void forEach(StringAnyConsumer visitor);

  /** Whether the object has {@code keyName}. */
  boolean hasKey(String keyName);

  /** The raw Gson object. */
  JsonObject getAsJsonObject();

  void raw(JsonObjectConsumer value);

  default <T> MappedValue<T> map(JsonObjectFunction<T> mapping) {
    return MappedValue.of(mapping.apply(getAsJsonObject()));
  }

  default <T> MappedValue<T> map(ObjValueFunction<T> mapping) {
    return MappedValue.of(mapping.apply(this));
  }

  /** Throws if {@code keyName} is present. */
  default ObjValue noKey(String keyName, Supplier<RuntimeException> exception) {
    if (hasKey(keyName)) throw exception.get();
    return this;
  }

  /** Throws if more than one of {@code keys} is present. */
  default ObjValue mutex(List<String> keys, Supplier<RuntimeException> exception) {
    String found = null;
    for (String key : keys) {
      if (hasKey(key)) {
        if (found != null) throw exception.get();

        found = key;
      }
    }
    return this;
  }

  /** Throws unless exactly one of {@code keys} is present. */
  default ObjValue requireExactlyOne(List<String> keys, Supplier<RuntimeException> exception) {
    return requireExactlyOne(keys, exception, exception);
  }

  /**
   * Throws {@code tooManyException} if several of {@code keys} are present, or {@code
   * notEnoughException} if none is.
   */
  default ObjValue requireExactlyOne(
      List<String> keys,
      Supplier<RuntimeException> tooManyException,
      Supplier<RuntimeException> notEnoughException) {
    String found = null;
    for (String key : keys) {
      if (hasKey(key)) {
        if (found != null) throw tooManyException.get();

        found = key;
      }
    }
    if (found == null) throw notEnoughException.get();
    return this;
  }
}
