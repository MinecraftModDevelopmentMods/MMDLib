package zone.moddev.mc.lib.common.builders;

import net.minecraft.resources.ResourceLocation;
import java.util.Map;

/** Fluent builders return new versions; callers must retain the returned value. */
public abstract class Builder<T, X> {
    public abstract T build();
    public abstract X setName(ResourceLocation name);
    public abstract X setName(String name);
    public abstract ResourceLocation getRegistryName();
    public abstract X setParent(ResourceLocation parent);
    public abstract X setEventMap(Map<String, ResourceLocation> events);
}
