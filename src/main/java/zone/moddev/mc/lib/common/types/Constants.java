package zone.moddev.mc.lib.common.types;

import net.minecraft.resources.ResourceLocation;

/**
 * Classpath locations used by the older {@link zone.moddev.mc.lib.util.DataLoaders} file loading
 * ({@code assets/mmdlib-data/<kind>}). Current definitions load from datapack sections through
 * {@link zone.moddev.mc.lib.common.loading.DefinitionLoader} instead.
 */
public class Constants {
  /** The individual path segments. */
  public static class stringPathBits {
    public static String basePath = "mmdlib-data";
    public static String assetsPath = "assets";
    public static String materialsPath = LocationType.MATERIAL.getMyTypeName();
    public static String itemsPath = LocationType.ITEM.getMyTypeName();
    public static String blocksPath = LocationType.BLOCK.getMyTypeName();
    public static String configPath = LocationType.CONFIG.getMyTypeName();
  }
  ;

  /** The full classpath directories for each {@link LocationType}. */
  public static class PathBits {
    public static String materials =
        String.format(
            "%s/%s/%s",
            stringPathBits.assetsPath, stringPathBits.basePath, stringPathBits.materialsPath);
    public static String items =
        String.format(
            "%s/%s/%s",
            stringPathBits.assetsPath, stringPathBits.basePath, stringPathBits.itemsPath);
    public static String blocks =
        String.format(
            "%s/%s/%s",
            stringPathBits.assetsPath, stringPathBits.basePath, stringPathBits.blocksPath);
    public static String config =
        String.format(
            "%s/%s/%s",
            stringPathBits.assetsPath, stringPathBits.basePath, stringPathBits.configPath);
  }
  ;

  /** Well-known IDs. */
  public static class Locations {
    public static ResourceLocation materialConfigBase = ResourceLocation.parse("mmdlib:material");
  }
}
