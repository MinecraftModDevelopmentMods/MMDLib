package zone.moddev.mc.lib.gui.drawer;

import java.util.Objects;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.model.Element;

/** Common-side drawer declaration; no client classes, mutable state or storage snapshots. */
public record DrawerDefinition(
    ResourceLocation type,
    String id,
    DrawerPlacement placement,
    Optional<Element> element,
    int toolbarWidth,
    boolean interactive) {
  public DrawerDefinition {
    Objects.requireNonNull(type);
    Objects.requireNonNull(id);
    Objects.requireNonNull(placement);
    Objects.requireNonNull(element);
    if (id.isBlank() || toolbarWidth < 0 || toolbarWidth > 8192)
      throw new IllegalArgumentException("Invalid drawer definition");
  }

  public DrawerDefinition(ResourceLocation type, String id, DrawerPlacement placement) {
    this(type, id, placement, Optional.empty(), 0, true);
  }
}
