package zone.moddev.mc.lib.common.builders;

import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.lib.common.types.MMDFluid;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable templates for a material's source/flowing fluid, optional liquid block and bucket. */
public final class FluidBuilder extends Builder<MMDFluid, FluidBuilder> {
    public static final FluidBuilder INSTANCE = new FluidBuilder(Map.of());
    private enum Key { NAME, MATERIAL, PARENT, TYPE, FLOW, APPEARANCE, HAS_BLOCK, BLOCK, HAS_BUCKET, BUCKET, EVENTS }
    private final Map<Key, Object> values;
    private FluidBuilder(Map<Key, Object> values) { this.values = Map.copyOf(values); }
    public static FluidBuilder create() { return INSTANCE; }
    @SuppressWarnings("unchecked")
    private <T> T value(Key key, T fallback) { return (T) values.getOrDefault(key, fallback); }
    private FluidBuilder with(Key key, Object value) {
        var copy = new EnumMap<Key, Object>(Key.class);
        copy.putAll(values); copy.put(key, Objects.requireNonNull(value));
        return new FluidBuilder(copy);
    }
    @Override public FluidBuilder setName(ResourceLocation name) { return with(Key.NAME, name); }
    @Override public FluidBuilder setName(String name) { return setName(ResourceLocation.parse(name)); }
    public ResourceLocation getTemplateName() { return value(Key.NAME, null); }
    public ResourceLocation getMaterialName() { return value(Key.MATERIAL, null); }
    public FluidBuilder forMaterial(ResourceLocation material) { return with(Key.MATERIAL, material); }
    public FluidBuilder forMaterial(String material) { return forMaterial(ResourceLocation.parse(material)); }
    @Override public ResourceLocation getRegistryName() {
        var template = getTemplateName(); var material = getMaterialName();
        return template == null || material == null ? null : ResourceLocation.fromNamespaceAndPath(
                material.getNamespace(), material.getPath() + "_" + template.getPath());
    }
    @Override public FluidBuilder setParent(ResourceLocation parent) { return with(Key.PARENT, parent); }
    public FluidBuilder setTypeProperties(MMDFluid.TypeProperties properties) { return with(Key.TYPE, properties); }
    public FluidBuilder setFlowProperties(MMDFluid.FlowProperties properties) { return with(Key.FLOW, properties); }
    public FluidBuilder setAppearance(MMDFluid.Appearance appearance) { return with(Key.APPEARANCE, appearance); }
    public MMDFluid.TypeProperties getTypeProperties() { return value(Key.TYPE, MMDFluid.TypeProperties.DEFAULT); }
    public MMDFluid.FlowProperties getFlowProperties() { return value(Key.FLOW, MMDFluid.FlowProperties.DEFAULT); }
    public MMDFluid.Appearance getAppearance() { return value(Key.APPEARANCE, MMDFluid.Appearance.DEFAULT); }
    public FluidBuilder setDensity(int value) {
        var p = getTypeProperties();
        return setTypeProperties(new MMDFluid.TypeProperties(value, p.viscosity(), p.temperature(), p.lightLevel(), p.motionScale(), p.canSwim(), p.canDrown(), p.canExtinguish(), p.supportsBoating(), p.canHydrate()));
    }
    public FluidBuilder setViscosity(int value) {
        var p = getTypeProperties();
        return setTypeProperties(new MMDFluid.TypeProperties(p.density(), value, p.temperature(), p.lightLevel(), p.motionScale(), p.canSwim(), p.canDrown(), p.canExtinguish(), p.supportsBoating(), p.canHydrate()));
    }
    public FluidBuilder setTemperature(int value) {
        var p = getTypeProperties();
        return setTypeProperties(new MMDFluid.TypeProperties(p.density(), p.viscosity(), value, p.lightLevel(), p.motionScale(), p.canSwim(), p.canDrown(), p.canExtinguish(), p.supportsBoating(), p.canHydrate()));
    }
    public FluidBuilder setLightLevel(int value) {
        var p = getTypeProperties();
        return setTypeProperties(new MMDFluid.TypeProperties(p.density(), p.viscosity(), p.temperature(), value, p.motionScale(), p.canSwim(), p.canDrown(), p.canExtinguish(), p.supportsBoating(), p.canHydrate()));
    }
    public FluidBuilder setMotionScale(double value) {
        var p = getTypeProperties();
        return setTypeProperties(new MMDFluid.TypeProperties(p.density(), p.viscosity(), p.temperature(), p.lightLevel(), value, p.canSwim(), p.canDrown(), p.canExtinguish(), p.supportsBoating(), p.canHydrate()));
    }
    public FluidBuilder setCanSwim(boolean value) {
        var p = getTypeProperties();
        return setTypeProperties(new MMDFluid.TypeProperties(p.density(), p.viscosity(), p.temperature(), p.lightLevel(), p.motionScale(), value, p.canDrown(), p.canExtinguish(), p.supportsBoating(), p.canHydrate()));
    }
    public FluidBuilder setCanDrown(boolean value) {
        var p = getTypeProperties();
        return setTypeProperties(new MMDFluid.TypeProperties(p.density(), p.viscosity(), p.temperature(), p.lightLevel(), p.motionScale(), p.canSwim(), value, p.canExtinguish(), p.supportsBoating(), p.canHydrate()));
    }
    public FluidBuilder setCanExtinguish(boolean value) {
        var p = getTypeProperties();
        return setTypeProperties(new MMDFluid.TypeProperties(p.density(), p.viscosity(), p.temperature(), p.lightLevel(), p.motionScale(), p.canSwim(), p.canDrown(), value, p.supportsBoating(), p.canHydrate()));
    }
    public FluidBuilder setSupportsBoating(boolean value) {
        var p = getTypeProperties();
        return setTypeProperties(new MMDFluid.TypeProperties(p.density(), p.viscosity(), p.temperature(), p.lightLevel(), p.motionScale(), p.canSwim(), p.canDrown(), p.canExtinguish(), value, p.canHydrate()));
    }
    public FluidBuilder setCanHydrate(boolean value) {
        var p = getTypeProperties();
        return setTypeProperties(new MMDFluid.TypeProperties(p.density(), p.viscosity(), p.temperature(), p.lightLevel(), p.motionScale(), p.canSwim(), p.canDrown(), p.canExtinguish(), p.supportsBoating(), value));
    }
    public FluidBuilder setSlopeFindDistance(int value) {
        var p = getFlowProperties();
        return setFlowProperties(new MMDFluid.FlowProperties(value, p.levelDecreasePerBlock(), p.tickRate(), p.explosionResistance(), p.canConvertToSource()));
    }
    public FluidBuilder setLevelDecreasePerBlock(int value) {
        var p = getFlowProperties();
        return setFlowProperties(new MMDFluid.FlowProperties(p.slopeFindDistance(), value, p.tickRate(), p.explosionResistance(), p.canConvertToSource()));
    }
    public FluidBuilder setTickRate(int value) {
        var p = getFlowProperties();
        return setFlowProperties(new MMDFluid.FlowProperties(p.slopeFindDistance(), p.levelDecreasePerBlock(), value, p.explosionResistance(), p.canConvertToSource()));
    }
    public FluidBuilder setExplosionResistance(float value) {
        var p = getFlowProperties();
        return setFlowProperties(new MMDFluid.FlowProperties(p.slopeFindDistance(), p.levelDecreasePerBlock(), p.tickRate(), value, p.canConvertToSource()));
    }
    public FluidBuilder setCanConvertToSource(boolean value) {
        var p = getFlowProperties();
        return setFlowProperties(new MMDFluid.FlowProperties(p.slopeFindDistance(), p.levelDecreasePerBlock(), p.tickRate(), p.explosionResistance(), value));
    }
    public FluidBuilder setTextures(ResourceLocation still, ResourceLocation flowing) {
        var p = getAppearance(); return setAppearance(new MMDFluid.Appearance(still, flowing, p.overlay(), p.tint()));
    }
    public FluidBuilder setOverlay(Optional<ResourceLocation> overlay) {
        var p = getAppearance(); return setAppearance(new MMDFluid.Appearance(p.still(), p.flowing(), overlay, p.tint()));
    }
    public FluidBuilder setTintColor(int argb) {
        var p = getAppearance(); return setAppearance(new MMDFluid.Appearance(p.still(), p.flowing(), p.overlay(), argb));
    }
    public FluidBuilder setHasBlock(boolean enabled) { return with(Key.HAS_BLOCK, enabled); }
    public FluidBuilder setBlockTemplate(BlockBuilder block) { return with(Key.BLOCK, block).setHasBlock(true); }
    public FluidBuilder setHasBucket(boolean enabled) { return with(Key.HAS_BUCKET, enabled); }
    public FluidBuilder setBucketTemplate(ItemBuilder bucket) { return with(Key.BUCKET, bucket).setHasBucket(true); }
    @Override public FluidBuilder setEventMap(Map<String, ResourceLocation> events) {
        events.forEach((event, action) -> {
            if (event.isBlank()) throw new IllegalArgumentException("Blank event name");
            Objects.requireNonNull(action);
        });
        return with(Key.EVENTS, Map.copyOf(events));
    }
    public MMDFluid buildFor(ResourceLocation material) { return forMaterial(material).build(); }
    @Override public MMDFluid build() {
        if (getTemplateName() == null) throw new IllegalStateException("Fluid template needs a name");
        if (getMaterialName() == null) throw new IllegalStateException("Fluid template must be bound to a material");
        var block = value(Key.HAS_BLOCK, true) ? value(Key.BLOCK, BlockBuilder.create().setHardness(100)
                .setCollision(false).setOcclusion(false)).setName(getTemplateName()).forMaterial(getMaterialName())
                .setHasItem(false).build() : null;
        var bucketName = ResourceLocation.fromNamespaceAndPath(getTemplateName().getNamespace(), getTemplateName().getPath() + "_bucket");
        var bucket = value(Key.HAS_BUCKET, true) ? value(Key.BUCKET, ItemBuilder.create()).setName(bucketName)
                .forMaterial(getMaterialName()).setMaxStackSize(1).build() : null;
        return new MMDFluid(getRegistryName(), getTemplateName(), getMaterialName(), value(Key.PARENT, null),
                getTypeProperties(), getFlowProperties(), getAppearance(), block, bucket, value(Key.EVENTS, Map.of()));
    }
}
