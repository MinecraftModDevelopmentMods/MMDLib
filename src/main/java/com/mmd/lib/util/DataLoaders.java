package com.mmd.lib.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mmd.lib.common.types.LocationType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class DataLoaders {
    // Use GSON to parse a string, return the JsonElement that results
    protected static JsonElement parseJson(final String inputData) {
        return (new JsonParser()).parse(inputData);
    }

    protected static Boolean doesObjectHaveField(final JsonObject obj, final String fieldName) {
        return obj.has(fieldName);
    }

    protected static Boolean isFieldAPrimitive(final JsonObject obj, final String fieldName) {
        return obj.get(fieldName).isJsonPrimitive();
    }

    protected static Boolean doesFieldExistAndIsPrimitive(final JsonObject obj, final String fieldName) {
        return doesObjectHaveField(obj, fieldName) && isFieldAPrimitive(obj, fieldName);
    }

    protected static Boolean typeFieldExists(final JsonObject obj) {
        return doesFieldExistAndIsPrimitive(obj, "type");
    }

    protected static Boolean typeIsItem(final JsonObject obj) {
        return obj.get("type").getAsString().matches("item");
    }
    protected static String loadItemFile(final ResourceLocation rl) {
        return DataHelpers.loadFile(rl, "json", LocationType.ITEM);
    }

    // scan for and load Item JSON's from disk, registering to the internal registry
    public static void loadItems() {
        // Note for me in the future: This is done in two steps to make sure we get a _single_ list...
        // Yes, it's tempting to reduce this to a single long line of chained calls, but this feels easier to
        // maintain.
        List<ResourceLocation> foundItems = new LinkedList<>();
        ModList.get().getModFiles().stream().forEach(mfi -> foundItems.addAll(DataHelpers.buildFileList(mfi, LocationType.ITEM)));

        // we now have a list of, potentially, _every_ mods added item json's
        // final resolution: load them all by calling a codec and registering them with our internal registry
        List<JsonObject> loadedData = foundItems.parallelStream()
                // convert from the incoming resource location to a string containing the data
                .map(DataLoaders::loadItemFile)
                // parse the JSON
                .map(DataLoaders::parseJson)
                // make sure we get an Object
                .filter(elem -> elem.isJsonObject())
                // convert to an object
                .map(elem -> elem.getAsJsonObject())
                // make sure it has a "type" field
                .filter(DataLoaders::typeFieldExists)
                // make sure said field says "item"
                .filter(DataLoaders::typeIsItem)
                // convert to a list here...
                .collect(Collectors.toList());
        // call the Item Codec to convert the list of loaded Json's into a List<MMDItem> or a Map<ResourceLocation,MMDItem>
        // in fact... the above should probably pass the RL from the first stage through in the first `map` and all
        // following...
    }
}
