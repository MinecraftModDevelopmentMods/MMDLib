package zone.moddev.mc.lib.api.automation;

import java.util.Objects;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.api.capabilities.IoMode;

/** The same direction/content predicate is used by the menu, storage and running addon. */
public record AddonSpec(
    ResourceLocation type,
    ResourceType<?> resource,
    IoMode direction,
    int batch,
    int interval,
    long energyPerUnit,
    boolean filters) {
  public AddonSpec {
    Objects.requireNonNull(type);
    Objects.requireNonNull(resource);
    Objects.requireNonNull(direction);
    if (direction == IoMode.DISABLED || batch <= 0 || interval <= 0 || energyPerUnit < 0)
      throw new IllegalArgumentException("Invalid transfer addon specification");
  }

  public boolean accepts(ResourceGroup<?> group) {
    return group.type() == resource && group.supports(direction);
  }
}
