package zone.moddev.mc.lib.common.types;

import net.minecraft.resources.ResourceLocation;

public class Constants {
    public static class stringPathBits {
        public static String basePath = "mmdlib-data";
        public static String assetsPath = "assets";
        public static String materialsPath = LocationType.MATERIAL.getMyTypeName();
        public static String itemsPath = LocationType.ITEM.getMyTypeName();
        public static String blocksPath = LocationType.BLOCK.getMyTypeName();
        public static String configPath = LocationType.CONFIG.getMyTypeName();
    };

    public static class PathBits {
        public static String materials = String.format("%s/%s/%s", stringPathBits.assetsPath, stringPathBits.basePath, stringPathBits.materialsPath);
        public static String items = String.format("%s/%s/%s", stringPathBits.assetsPath, stringPathBits.basePath, stringPathBits.itemsPath);
        public static String blocks = String.format("%s/%s/%s", stringPathBits.assetsPath, stringPathBits.basePath, stringPathBits.blocksPath);
        public static String config = String.format("%s/%s/%s", stringPathBits.assetsPath, stringPathBits.basePath, stringPathBits.configPath);
    };

    public static class Locations {
        public static ResourceLocation materialConfigBase = ResourceLocation.parse("mmdlib:material");
    }
}
