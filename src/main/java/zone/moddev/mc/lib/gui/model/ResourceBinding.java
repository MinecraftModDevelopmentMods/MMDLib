package zone.moddev.mc.lib.gui.model;

import java.util.Map;

/** A screen-authored resource reference, shared by elements, validation and group highlights. */
public record ResourceBinding(String kind, int index) {
  public ResourceBinding {
    if (kind == null || kind.isBlank() || index < 0)
      throw new IllegalArgumentException("Invalid resource binding");
  }

  /** Unqualified built-in kinds use the mmdlib namespace; custom kinds may be namespaced. */
  public net.minecraft.resources.ResourceLocation groupType() {
    return net.minecraft.resources.ResourceLocation.parse(
        kind.contains(":") ? kind : "mmdlib:" + kind);
  }

  public void validate(Map<String, Integer> counts) {
    if (index >= counts.getOrDefault(kind, 0))
      throw new IllegalArgumentException("Resource index outside " + kind + " storage: " + index);
  }
}
