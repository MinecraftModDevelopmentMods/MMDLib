package zone.moddev.mc.lib.common.types;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.DataResult;

/** Immutable deferred codec input. The selected Java factory defines the settings schema. */
public final class FactoryData {
    public static final FactoryData EMPTY = new FactoryData(new JsonObject());
    public static final Codec<FactoryData> CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> new FactoryData(dynamic.convert(JsonOps.INSTANCE).getValue()),
            data -> new Dynamic<>(JsonOps.INSTANCE, data.json()));
    private final JsonElement value;
    public FactoryData(JsonElement value) { this.value = java.util.Objects.requireNonNull(value).deepCopy(); }
    public JsonElement json() { return value.deepCopy(); }
    public <S> DataResult<S> decode(Codec<S> codec) { return decode(codec, JsonOps.INSTANCE); }
    public <S> DataResult<S> decode(Codec<S> codec, DynamicOps<JsonElement> ops) {
        return codec.parse(ops, json());
    }
    @Override public boolean equals(Object other) { return other instanceof FactoryData data && value.equals(data.value); }
    @Override public int hashCode() { return value.hashCode(); }
}
