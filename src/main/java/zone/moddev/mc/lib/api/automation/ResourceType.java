package zone.moddev.mc.lib.api.automation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/** Extensible resource identity and the capability interface used by its transfer addons. */
public final class ResourceType<T> {
  private static final Map<ResourceLocation, ResourceType<?>> TYPES = new LinkedHashMap<>();
  public static final ResourceType<IItemHandler> ITEMS =
      register("mmdlib:items", IItemHandler.class);
  public static final ResourceType<IFluidHandler> FLUIDS =
      register("mmdlib:fluids", IFluidHandler.class);
  public static final ResourceType<IEnergyStorage> ENERGY =
      register("mmdlib:energy", IEnergyStorage.class);
  private final ResourceLocation id;
  private final Class<T> handlerType;

  private ResourceType(ResourceLocation id, Class<T> handlerType) {
    this.id = id;
    this.handlerType = handlerType;
  }

  /** Register during mod construction on both sides; future resource types need no UI changes. */
  public static synchronized <T> ResourceType<T> register(String id, Class<T> handlerType) {
    var key = ResourceLocation.parse(id);
    if (TYPES.containsKey(key))
      throw new IllegalArgumentException("Duplicate resource type: " + key);
    var type = new ResourceType<>(key, Objects.requireNonNull(handlerType));
    TYPES.put(key, type);
    return type;
  }

  public ResourceLocation id() {
    return id;
  }

  public Class<T> handlerType() {
    return handlerType;
  }

  public static ResourceType<?> require(ResourceLocation id) {
    var type = TYPES.get(id);
    if (type == null) throw new IllegalArgumentException("Unknown resource type: " + id);
    return type;
  }
}
