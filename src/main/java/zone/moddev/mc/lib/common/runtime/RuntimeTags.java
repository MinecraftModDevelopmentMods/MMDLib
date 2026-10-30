package zone.moddev.mc.lib.common.runtime;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Block tags MMDLib's runtime behaviors use. */
public final class RuntimeTags {
  private RuntimeTags() {}

  /**
   * {@code mmdlib:scythe_harvestable}: blocks a scythe mines quickly and breaks in a 3x3 area
   * around the one it breaks.
   */
  public static final TagKey<Block> SCYTHE_HARVESTABLE =
      TagKey.create(Registries.BLOCK, ResourceLocation.parse("mmdlib:scythe_harvestable"));

  /**
   * {@code mmdlib:crackhammer_crushable}: blocks a crackhammer mines quickly (its {@code fast_on}
   * tag).
   */
  public static final TagKey<Block> CRACKHAMMER_CRUSHABLE =
      TagKey.create(Registries.BLOCK, ResourceLocation.parse("mmdlib:crackhammer_crushable"));
}
