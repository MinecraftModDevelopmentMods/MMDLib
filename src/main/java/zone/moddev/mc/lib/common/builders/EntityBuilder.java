package zone.moddev.mc.lib.common.builders;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.MobCategory;
import zone.moddev.mc.lib.common.types.FactoryData;
import zone.moddev.mc.lib.common.types.MMDEntity;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Shared empty root and immutable entity templates for material loading. */
public final class EntityBuilder extends Builder<MMDEntity, EntityBuilder> {
    public static final EntityBuilder INSTANCE = new EntityBuilder(Map.of());
    private enum Key { NAME, MATERIAL, PARENT, FACTORY, CATEGORY, WIDTH, HEIGHT, TRACKING, INTERVAL,
        SAVE, SUMMON, FIRE_IMMUNE, DATA, EVENTS }
    private final Map<Key, Object> values;
    private EntityBuilder(Map<Key, Object> values) { this.values = Map.copyOf(values); }
    public static EntityBuilder create() { return INSTANCE; }
    @SuppressWarnings("unchecked")
    private <T> T value(Key key, T fallback) { return (T) values.getOrDefault(key, fallback); }
    private EntityBuilder with(Key key, Object value) {
        var next = new EnumMap<Key, Object>(Key.class);
        next.putAll(values);
        next.put(key, Objects.requireNonNull(value, key.name()));
        return new EntityBuilder(next);
    }
    @Override public EntityBuilder setName(ResourceLocation name) { return with(Key.NAME, name); }
    @Override public EntityBuilder setName(String name) { return setName(ResourceLocation.parse(name)); }
    public ResourceLocation getTemplateName() { return value(Key.NAME, null); }
    public ResourceLocation getMaterialName() { return value(Key.MATERIAL, null); }
    public EntityBuilder forMaterial(ResourceLocation material) { return with(Key.MATERIAL, material); }
    public EntityBuilder forMaterial(String material) { return forMaterial(ResourceLocation.parse(material)); }
    @Override public ResourceLocation getRegistryName() {
        var template = getTemplateName();
        var material = getMaterialName();
        return template == null || material == null ? null : ResourceLocation.fromNamespaceAndPath(
                material.getNamespace(), material.getPath() + "_" + template.getPath());
    }
    @Override public EntityBuilder setParent(ResourceLocation parent) { return with(Key.PARENT, parent); }
    public EntityBuilder setFactory(ResourceLocation factory) { return with(Key.FACTORY, factory); }
    public EntityBuilder setFactory(String factory) { return setFactory(ResourceLocation.parse(factory)); }
    public EntityBuilder setFactoryData(FactoryData data) { return with(Key.DATA, data); }
    public EntityBuilder setCategory(MobCategory category) { return with(Key.CATEGORY, category); }
    public EntityBuilder setDimensions(float width, float height) {
        MMDEntity.validateDimensions(width, height);
        return with(Key.WIDTH, width).with(Key.HEIGHT, height);
    }
    public EntityBuilder setTrackingRange(int range) {
        if (range < 0) throw new IllegalArgumentException("Tracking range must not be negative");
        return with(Key.TRACKING, range);
    }
    public EntityBuilder setUpdateInterval(int ticks) {
        if (ticks < 1) throw new IllegalArgumentException("Update interval must be positive");
        return with(Key.INTERVAL, ticks);
    }
    public EntityBuilder setSave(boolean save) { return with(Key.SAVE, save); }
    public EntityBuilder setSummon(boolean summon) { return with(Key.SUMMON, summon); }
    public EntityBuilder setFireImmune(boolean immune) { return with(Key.FIRE_IMMUNE, immune); }
    @Override public EntityBuilder setEventMap(Map<String, ResourceLocation> events) {
        events.forEach((event, action) -> {
            if (event.isBlank()) throw new IllegalArgumentException("Event name must not be blank");
            Objects.requireNonNull(action, "event action");
        });
        return with(Key.EVENTS, Map.copyOf(events));
    }
    public MMDEntity buildFor(ResourceLocation material) { return forMaterial(material).build(); }
    @Override public MMDEntity build() {
        if (getTemplateName() == null) throw new IllegalStateException("Entity template needs a name");
        if (getMaterialName() == null) throw new IllegalStateException("Entity template must be bound to a material");
        ResourceLocation factory = value(Key.FACTORY, null);
        if (factory == null) throw new IllegalStateException("Entity template needs a factory ID");
        return new MMDEntity(getRegistryName(), getTemplateName(), getMaterialName(), value(Key.PARENT, null), factory,
                value(Key.CATEGORY, MobCategory.MISC), value(Key.WIDTH, .5F), value(Key.HEIGHT, .5F),
                value(Key.TRACKING, 4), value(Key.INTERVAL, 3), value(Key.SAVE, true), value(Key.SUMMON, true),
                value(Key.FIRE_IMMUNE, false), value(Key.DATA, FactoryData.EMPTY), value(Key.EVENTS, Map.of()));
    }
}
