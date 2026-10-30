package zone.moddev.mc.lib.common.types;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

/**
 * The raw {@code factory_data} (or {@code entity_data}) JSON of a module, kept undecoded until the
 * Java implementation it belongs to decodes it with its own codec. Immutable: the JSON is copied in
 * and out.
 */
public final class FactoryData {
  /** An empty JSON object; what a module without {@code factory_data} has. */
  public static final FactoryData EMPTY = new FactoryData(new JsonObject());

  /** Reads any JSON value as is, and writes it back unchanged. */
  public static final Codec<FactoryData> CODEC =
      Codec.PASSTHROUGH.xmap(
          dynamic -> new FactoryData(dynamic.convert(JsonOps.INSTANCE).getValue()),
          data -> new Dynamic<>(JsonOps.INSTANCE, data.json()));

  private final JsonElement value;

  /** Wraps a copy of {@code value}. */
  public FactoryData(JsonElement value) {
    this.value = java.util.Objects.requireNonNull(value).deepCopy();
  }

  /** A copy of the JSON, safe to modify. */
  public JsonElement json() {
    return value.deepCopy();
  }

  /**
   * This data with each top-level key of {@code overrides} replacing or adding the same key.
   *
   * @throws IllegalArgumentException if either is not a JSON object
   */
  public FactoryData overlay(FactoryData overrides) {
    if (!value.isJsonObject() || !overrides.value.isJsonObject())
      throw new IllegalArgumentException("Only object factory_data can be merged");
    var result = value.deepCopy().getAsJsonObject();
    overrides.value.getAsJsonObject().entrySet().forEach(e -> result.add(e.getKey(), e.getValue()));
    return new FactoryData(result);
  }

  /** Decodes the settings with {@code codec}. */
  public <S> DataResult<S> decode(Codec<S> codec) {
    return decode(codec, JsonOps.INSTANCE);
  }

  /**
   * Decodes the settings with {@code codec} and the given ops, e.g. {@code RegistryOps} for
   * settings that refer to registry entries.
   */
  public <S> DataResult<S> decode(Codec<S> codec, DynamicOps<JsonElement> ops) {
    return codec.parse(ops, json());
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof FactoryData data && value.equals(data.value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }
}
