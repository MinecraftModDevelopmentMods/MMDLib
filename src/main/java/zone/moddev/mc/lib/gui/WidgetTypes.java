package zone.moddev.mc.lib.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import net.minecraft.resources.ResourceLocation;

/** Common-side widget contracts. No renderer or other client class belongs in this registry. */
public final class WidgetTypes {
  /** Register mod widgets here from the mod constructor, on both physical sides. */
  public static final WidgetTypes GLOBAL = new WidgetTypes();

  private final Map<ResourceLocation, WidgetType> types = new HashMap<>();
  private boolean frozen;

  /** Registers one type. IDs cannot be replaced, even with the same definition. */
  public synchronized void register(ResourceLocation id, WidgetType type) {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(type, "type");
    if (frozen)
      throw new IllegalStateException("Widget types are frozen; register during mod construction");
    if (types.putIfAbsent(id, type) != null)
      throw new IllegalArgumentException("Duplicate widget type: " + id);
  }

  /** Finds a type, failing explicitly for an unavailable mod or a misspelled ID. */
  public synchronized WidgetType require(ResourceLocation id) {
    var type = types.get(id);
    if (type == null) throw new IllegalArgumentException("Unknown widget type: " + id);
    return type;
  }

  /** Called before startup screen discovery so all screens use the same contracts. */
  public synchronized void freeze() {
    frozen = true;
  }
}
