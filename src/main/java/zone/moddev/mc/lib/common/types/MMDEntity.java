package zone.moddev.mc.lib.common.types;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobCategory;
import java.util.Map;
import java.util.Objects;

/** Material-bound entity type definition; no live entity or registry entry is created here. */
public record MMDEntity(ResourceLocation name, ResourceLocation template, ResourceLocation material,
        ResourceLocation parent, ResourceLocation factory, MobCategory category, float width, float height,
        int trackingRange, int updateInterval, boolean save, boolean summon, boolean fireImmune,
        FactoryData factoryData, Map<String, ResourceLocation> events) {
    public MMDEntity {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(factoryData, "factoryData");
        validateDimensions(width, height);
        if (trackingRange < 0 || updateInterval < 1)
            throw new IllegalArgumentException("Tracking range must be nonnegative and update interval positive");
        events = Map.copyOf(events);
        if (events.keySet().stream().anyMatch(String::isBlank))
            throw new IllegalArgumentException("Entity event names must not be blank");
    }
    public static void validateDimensions(float width, float height) {
        if (!Float.isFinite(width) || !Float.isFinite(height) || width < 0 || height < 0)
            throw new IllegalArgumentException("Entity dimensions must be finite and nonnegative");
    }
}
