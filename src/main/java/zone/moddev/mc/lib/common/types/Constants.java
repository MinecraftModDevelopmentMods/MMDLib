package zone.moddev.mc.lib.common.types;

import net.minecraft.resources.ResourceLocation;

/**
 * Classpath locations used by the older {@link zone.moddev.mc.lib.util.DataLoaders} file loading
 * ({@code assets/mmdlib-data/<kind>}). Current definitions load from datapack sections through
 * {@link zone.moddev.mc.lib.common.loading.DefinitionLoader} instead.
 */
public class Constants {
  private Constants() {}

  /** The individual path segments. */
  public static class stringPathBits {
    private stringPathBits() {}

    /** The directory under {@code assets} that holds the older data: {@code mmdlib-data}. */
    public static String basePath = "mmdlib-data";

    /** The top-level classpath directory: {@code assets}. */
    public static String assetsPath = "assets";

    /** The material directory name. */
    public static String materialsPath = LocationType.MATERIAL.getMyTypeName();

    /** The item directory name. */
    public static String itemsPath = LocationType.ITEM.getMyTypeName();

    /** The block directory name. */
    public static String blocksPath = LocationType.BLOCK.getMyTypeName();

    /** The config directory name. */
    public static String configPath = LocationType.CONFIG.getMyTypeName();
  }
  ;

  /** The full classpath directories for each {@link LocationType}. */
  public static class PathBits {
    private PathBits() {}

    /** {@code assets/mmdlib-data/material}. */
    public static String materials =
        String.format(
            "%s/%s/%s",
            stringPathBits.assetsPath, stringPathBits.basePath, stringPathBits.materialsPath);

    /** {@code assets/mmdlib-data/item}. */
    public static String items =
        String.format(
            "%s/%s/%s",
            stringPathBits.assetsPath, stringPathBits.basePath, stringPathBits.itemsPath);

    /** {@code assets/mmdlib-data/block}. */
    public static String blocks =
        String.format(
            "%s/%s/%s",
            stringPathBits.assetsPath, stringPathBits.basePath, stringPathBits.blocksPath);

    /** {@code assets/mmdlib-data/config}. */
    public static String config =
        String.format(
            "%s/%s/%s",
            stringPathBits.assetsPath, stringPathBits.basePath, stringPathBits.configPath);
  }
  ;

  /** Well-known IDs. */
  public static class Locations {
    private Locations() {}

    /** The base ID of material config files: {@code mmdlib:material}. */
    public static ResourceLocation materialConfigBase = ResourceLocation.parse("mmdlib:material");
  }
}
