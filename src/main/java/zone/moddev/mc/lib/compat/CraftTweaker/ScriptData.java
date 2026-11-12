package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.data.*;
import com.blamejared.crafttweaker.api.data.visitor.DataVisitor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.nbt.NumericTag;

/**
 * Converts script data such as {@code {speed: 2, players: true}} to JSON for
 * {@code factory_data}. Booleans stay booleans, unlike a conversion through
 * NBT.
 */
final class ScriptData implements DataVisitor<JsonElement> {
    private static final ScriptData INSTANCE = new ScriptData();

    private ScriptData() {
    }

    static JsonElement json(IData data) {
        return data.accept(INSTANCE);
    }

    private static JsonElement number(IData data) {
        return new JsonPrimitive(
            ((NumericTag) data.getInternal()).getAsNumber());
    }

    private static JsonElement list(java.util.List<IData> values) {
        var array = new JsonArray();
        values.forEach(value -> array.add(json(value)));
        return array;
    }

    @Override
    public JsonElement visitBool(BoolData data) {
        return new JsonPrimitive(
            ((NumericTag) data.getInternal()).getAsByte() != 0);
    }

    @Override
    public JsonElement visitByteArray(ByteArrayData data) {
        return list(data.asList());
    }

    @Override
    public JsonElement visitByte(ByteData data) {
        return number(data);
    }

    @Override
    public JsonElement visitDouble(DoubleData data) {
        return number(data);
    }

    @Override
    public JsonElement visitFloat(FloatData data) {
        return number(data);
    }

    @Override
    public JsonElement visitIntArray(IntArrayData data) {
        return list(data.asList());
    }

    @Override
    public JsonElement visitInt(IntData data) {
        return number(data);
    }

    @Override
    public JsonElement visitList(ListData data) {
        return list(data.asList());
    }

    @Override
    public JsonElement visitLongArray(LongArrayData data) {
        return list(data.asList());
    }

    @Override
    public JsonElement visitLong(LongData data) {
        return number(data);
    }

    @Override
    public JsonElement visitMap(MapData data) {
        var object = new JsonObject();
        data.asMap().forEach((key, value) -> object.add(key, json(value)));
        return object;
    }

    @Override
    public JsonElement visitShort(ShortData data) {
        return number(data);
    }

    @Override
    public JsonElement visitString(StringData data) {
        return new JsonPrimitive(data.getInternal().getAsString());
    }

    @Override
    public JsonElement visitEmpty(EmptyData data) {
        return JsonNull.INSTANCE;
    }
}
