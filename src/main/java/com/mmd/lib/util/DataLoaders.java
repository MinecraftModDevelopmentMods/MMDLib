package com.mmd.lib.util;

import com.google.common.base.Joiner;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mmd.lib.common.types.Constants;
import com.mmd.lib.common.types.LocationType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.forgespi.language.IModFileInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static com.mmd.lib.MMDLib.LOGGER;

public class DataLoaders {
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

    public static String loadFile(ResourceLocation fileLoc, String extension, LocationType resourceType) {
        ModFile mf = (ModFile) ModList.get().getModFileById(fileLoc.getNamespace()).getFile();
        String pathRoot = getResourcePathBase(resourceType);
        String base = pathRoot.concat("/").concat(fileLoc.getPath()).concat(".").concat(extension);
        Path p = mf.getSecureJar().getPath(base);
        StringBuilder buffer = new StringBuilder();
        try(InputStream is = p.toUri().toURL().openStream();
            InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
            BufferedReader br = new BufferedReader(isr)) {
            br.lines().forEach(l -> buffer.append(String.format("\n%s",l)));
        } catch (IOException e) {
            LOGGER.error("Exception reading data: ", e);
            return "";
        }
        return buffer.toString().strip();
    }

    protected static String loadItemFile(final ResourceLocation rl) {
        return loadFile(rl, "json", LocationType.ITEM);
    }

    private static List<ResourceLocation> getMaterialsList(IModFileInfo info) {
        return buildFileList(info, LocationType.MATERIAL);
    }

    private static List<ResourceLocation> getBlocksList(IModFileInfo info) {
        return buildFileList(info, LocationType.BLOCK);
    }

    private static List<ResourceLocation> getItemsList(IModFileInfo info) {
        return buildFileList(info, LocationType.ITEM);
    }

    private static String getResourcePathBase(LocationType type) {
        return switch(type) {
            case MATERIAL -> Constants.PathBits.materials;
            case ITEM -> Constants.PathBits.items;
            case BLOCK -> Constants.PathBits.blocks;
            case CONFIG -> Constants.PathBits.config;
            case NONE -> "";
            default -> "";
        };
    }

    protected static List<ResourceLocation> buildFileList(IModFileInfo info, LocationType type) {
        String p = getResourcePathBase(type);

        if (p.isEmpty() || p.matches("")) return Collections.emptyList();

        Path root = info.getFile().getSecureJar().getPath(p);
        try {
            return Files.walk(root).map(root::relativize)//path -> root.relativize(path))
                    .filter(path -> path.getNameCount() <= 64) // logical bounds checking
                    .filter(path -> path.toString().toLowerCase(Locale.ENGLISH).endsWith(".json"))
                    .map(path -> Joiner.on('/').join(path))
                    .map(path -> path.substring(0, path.length() - 5)) // strip the extension
                    .map(path -> new ResourceLocation(info.getMods().get(0).getModId(), path))
                    .collect(Collectors.toList());
        } catch (IOException ex) {
            if (!(ex instanceof NoSuchFileException)) {
                LOGGER.error("Exception trying to iterate files", ex);
            }
            return Collections.emptyList();
        }
    }

    // scan for and load Item JSON's from disk, registering to the internal registry
    public static void loadItems() {
        // Note for me in the future: This is done in two steps to make sure we get a _single_ list...
        // Yes, it's tempting to reduce this to a single long line of chained calls, but this feels easier to
        // maintain.
        List<ResourceLocation> foundItems = new LinkedList<>();
        ModList.get().getModFiles().stream()
                .map(DataLoaders::getItemsList)
                .forEach(foundItems::addAll);

        // we now have a list of, potentially, _every_ mods added item json's
        // final resolution: load them all by calling a codec and registering them with our internal registry
        List<JsonObject> loadedData = foundItems.parallelStream()
                // convert from the incoming resource location to a string containing the data
                .map(DataLoaders::loadItemFile)
                // parse the JSON
                .map(DataLoaders::parseJson)
                // make sure we get an Object
                .filter(JsonElement::isJsonObject)
                // convert to an object
                .map(JsonElement::getAsJsonObject)
                // make sure it has a "type" field
                .filter(DataLoaders::typeFieldExists)
                // make sure said field says "item"
                .filter(DataLoaders::typeIsItem)
                // convert to a list here...
                .collect(Collectors.toList());

        // okay, now we pass this JsonObject to the "Parser" that runs the builder

        // call the Item Codec to convert the list of loaded Json's into a List<MMDItem> or a Map<ResourceLocation,MMDItem>
        // in fact... the above should probably pass the RL from the first stage through in the first `map` and all
        // following...
    }
}
