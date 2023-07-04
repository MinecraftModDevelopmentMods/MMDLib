package com.mmd.lib.common.types;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.schibsted.spt.data.jslt.Parser;
import com.schibsted.spt.data.jslt.Expression;
import com.fasterxml.jackson.core.JsonProcessingException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import static com.mmd.lib.MMDLib.LOGGER;
import static com.mmd.lib.common.codecs.MMDMaterialCodec.MMD_MATERIAL_CODEC;
import com.mmd.lib.util.DataLoaders;

public class MMDMaterial {
    private final Map<ResourceLocation, Item> items = new ConcurrentHashMap<ResourceLocation, Item>();
    private final Map<ResourceLocation, Block> blocks = new ConcurrentHashMap<ResourceLocation, Block>();

    private final Set<ResourceLocation> allowedBlocks = new ConcurrentSkipListSet<ResourceLocation>();
    private final Set<ResourceLocation> allowedItems = new ConcurrentSkipListSet<ResourceLocation>();

    // TODO:
    //       1) add material stat holding bits
    //       2) add callback handlers for various method overrides ?
    //          * This would need some scripting or other help -- needs thought, maybe ignore
    //       3) add MaterialBuilder so Codec can work

    public MMDMaterial() {
    }

    @Nullable
    public MMDMaterial load(final ResourceLocation toLoad) {
        // TODO: Load <resourceLocation> as `material` from `assets/mmdlib-data`
        String rawData = DataLoaders.loadFile(toLoad, "jslt", LocationType.MATERIAL);
        Expression jslt = Parser.compileString(rawData);
        JsonNode baseParsed = null;
        try {
            baseParsed = (new ObjectMapper()).readTree(DataLoaders.loadFile(Constants.Locations.materialConfigBase, "json", LocationType.CONFIG)).path(".");
            // setup the base "items" and "blocks" bits here ?
            // load a JSLT we can apply after we load in the available set of items&blocks
            // Instead of the above load, we should probably fill this in memory and then _write_ to things using the jackson Mapper bits
        } catch (JsonProcessingException e) {
            LOGGER.error("Error processing JSLT template or instantiating base parsing context", e);
            return null;
        }
        // baseParsed should apply to a context that defines all the various constants and helper values
        // along with extra helper functionality so that the actual, final run of applying the loaded material JSLT
        // acts to properly produce the correct output
        JsonNode applied = jslt.apply(baseParsed);
        String dataToLoad = applied.toPrettyString();
        JsonObject gsonData = (JsonObject) JsonParser.parseString(dataToLoad);
        return MMD_MATERIAL_CODEC.decode(JsonOps.INSTANCE, gsonData).getOrThrow(false, str -> {}).getFirst();
    }
}
