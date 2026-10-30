package zone.moddev.mc.lib.util;

import static zone.moddev.mc.lib.MMDLib.LOGGER;

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

import com.google.common.base.Joiner;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.neoforgespi.language.IModFileInfo;

import zone.moddev.mc.lib.common.types.Constants;
import zone.moddev.mc.lib.common.types.LocationType;

/**
 * The older classpath file loading, reading {@code assets/mmdlib-data/<kind>} from mod jars.
 * Superseded by {@link zone.moddev.mc.lib.common.loading.DefinitionLoader}; kept for {@link
 * zone.moddev.mc.lib.common.types.MMDMaterial#load}.
 */
public class DataLoaders {
  /** For subclasses; every method is static. */
  protected DataLoaders() {}

  /** Parses {@code inputData} as JSON. */
  protected static JsonElement parseJson(final String inputData) {
    return JsonParser.parseString(inputData);
  }

  /** Whether {@code obj} has {@code fieldName}. */
  protected static Boolean doesObjectHaveField(final JsonObject obj, final String fieldName) {
    return obj.has(fieldName);
  }

  /** Whether field {@code fieldName} of {@code obj} is a primitive. */
  protected static Boolean isFieldAPrimitive(final JsonObject obj, final String fieldName) {
    return obj.get(fieldName).isJsonPrimitive();
  }

  /** Whether {@code obj} has {@code fieldName} and it is a primitive. */
  protected static Boolean doesFieldExistAndIsPrimitive(
      final JsonObject obj, final String fieldName) {
    return doesObjectHaveField(obj, fieldName) && isFieldAPrimitive(obj, fieldName);
  }

  /** Whether {@code obj} has a primitive {@code type} field. */
  protected static Boolean typeFieldExists(final JsonObject obj) {
    return doesFieldExistAndIsPrimitive(obj, "type");
  }

  /** Whether {@code obj}'s {@code type} is {@code item}. */
  protected static Boolean typeIsItem(final JsonObject obj) {
    return obj.get("type").getAsString().matches("item");
  }

  /**
   * Reads a file from the jar of the mod named by {@code fileLoc}'s namespace.
   *
   * @return the file's text, or an empty string if it cannot be read (the error is logged)
   */
  public static String loadFile(
      ResourceLocation fileLoc, String extension, LocationType resourceType) {
    ModFile mf = (ModFile) ModList.get().getModFileById(fileLoc.getNamespace()).getFile();
    String pathRoot = getResourcePathBase(resourceType);
    String base = pathRoot.concat("/").concat(fileLoc.getPath()).concat(".").concat(extension);
    Path p = mf.getSecureJar().getPath(base);
    StringBuilder buffer = new StringBuilder();
    try (InputStream is = p.toUri().toURL().openStream();
        InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
        BufferedReader br = new BufferedReader(isr)) {
      br.lines().forEach(l -> buffer.append(String.format("\n%s", l)));
    } catch (IOException e) {
      LOGGER.error("Exception reading data: ", e);
      return "";
    }
    return buffer.toString().strip();
  }

  /** Reads an item file. */
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
    return switch (type) {
      case MATERIAL -> Constants.PathBits.materials;
      case ITEM -> Constants.PathBits.items;
      case BLOCK -> Constants.PathBits.blocks;
      case CONFIG -> Constants.PathBits.config;
      case NONE -> "";
      default -> "";
    };
  }

  /** The IDs of every {@code .json} file of {@code type} in a mod file. */
  protected static List<ResourceLocation> buildFileList(IModFileInfo info, LocationType type) {
    String p = getResourcePathBase(type);

    if (p.isEmpty() || p.matches("")) return Collections.emptyList();

    Path root = info.getFile().getSecureJar().getPath(p);
    try (var paths = Files.walk(root)) {
      return paths
          .filter(Files::isRegularFile)
          .map(root::relativize) // path -> root.relativize(path))
          .filter(path -> path.getNameCount() <= 64) // logical bounds checking
          .filter(path -> path.toString().toLowerCase(Locale.ENGLISH).endsWith(".json"))
          .map(path -> Joiner.on('/').join(path))
          .map(path -> path.substring(0, path.length() - 5)) // strip the extension
          .map(
              path -> ResourceLocation.fromNamespaceAndPath(info.getMods().get(0).getModId(), path))
          .collect(Collectors.toList());
    } catch (IOException ex) {
      if (!(ex instanceof NoSuchFileException)) {
        LOGGER.error("Exception trying to iterate files", ex);
      }
      return Collections.emptyList();
    }
  }

  // scan for and load Item JSON's from disk, registering to the internal registry
  /**
   * Reads every item file in every mod and keeps those whose {@code type} is {@code item}.
   * Unfinished: the results are not used yet.
   */
  public static void loadItems() {
    // Note for me in the future: This is done in two steps to make sure we get a _single_ list...
    // Yes, it's tempting to reduce this to a single long line of chained calls, but this feels
    // easier to
    // maintain.
    List<ResourceLocation> foundItems = new LinkedList<>();
    ModList.get().getModFiles().stream().map(DataLoaders::getItemsList).forEach(foundItems::addAll);

    // we now have a list of, potentially, _every_ mods added item json's
    // final resolution: load them all by calling a codec and registering them with our internal
    // registry
    List<JsonObject> loadedData =
        foundItems.parallelStream()
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

    // call the Item Codec to convert the list of loaded Json's into a List<MMDItem> or a
    // Map<ResourceLocation,MMDItem>
    // in fact... the above should probably pass the RL from the first stage through in the first
    // `map` and all
    // following...
  }
}
