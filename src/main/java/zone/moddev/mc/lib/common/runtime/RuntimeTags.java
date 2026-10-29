package zone.moddev.mc.lib.common.runtime;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Block tags MMDLib's runtime behaviors use. */
public final class RuntimeTags {
  private RuntimeTags() {}

  public static final TagKey<Block> SCYTHE_HARVESTABLE =
      TagKey.create(Registries.BLOCK, ResourceLocation.parse("mmdlib:scythe_harvestable"));
  public static final TagKey<Block> CRACKHAMMER_CRUSHABLE =
      TagKey.create(Registries.BLOCK, ResourceLocation.parse("mmdlib:crackhammer_crushable"));
}
