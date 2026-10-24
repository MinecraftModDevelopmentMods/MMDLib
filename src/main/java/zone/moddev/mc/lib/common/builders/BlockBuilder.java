package zone.moddev.mc.lib.common.builders;

import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.lib.common.types.MMDBlock;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Shared empty root; every operation returns a new material block template. */
public final class BlockBuilder extends Builder<MMDBlock, BlockBuilder> {
    public static final BlockBuilder INSTANCE = new BlockBuilder(Map.of());
    private enum Key { NAME, MATERIAL, PARENT, TYPE, PROPERTIES, TAGS, EVENTS, HAS_ITEM, ITEM }
    private final Map<Key, Object> values;
    private BlockBuilder(Map<Key, Object> values) { this.values = Map.copyOf(values); }
    public static BlockBuilder create() { return INSTANCE; }
    @SuppressWarnings("unchecked")
    private <T> T value(Key key, T fallback) { return (T) values.getOrDefault(key, fallback); }
    private BlockBuilder with(Key key, Object value) {
        var copy = new EnumMap<Key, Object>(Key.class);
        copy.putAll(values);
        copy.put(key, Objects.requireNonNull(value, key.name()));
        return new BlockBuilder(copy);
    }
    @Override public BlockBuilder setName(ResourceLocation name) { return with(Key.NAME, name); }
    @Override public BlockBuilder setName(String name) { return setName(ResourceLocation.parse(name)); }
    public ResourceLocation getTemplateName() { return value(Key.NAME, null); }
    public ResourceLocation getMaterialName() { return value(Key.MATERIAL, null); }
    public BlockBuilder forMaterial(ResourceLocation material) { return with(Key.MATERIAL, material); }
    public BlockBuilder forMaterial(String material) { return forMaterial(ResourceLocation.parse(material)); }
    @Override public ResourceLocation getRegistryName() {
        var material = getMaterialName();
        var template = getTemplateName();
        return material == null || template == null ? null : ResourceLocation.fromNamespaceAndPath(
                material.getNamespace(), material.getPath() + "_" + template.getPath());
    }
    @Override public BlockBuilder setParent(ResourceLocation parent) { return with(Key.PARENT, parent); }
    public BlockBuilder setType(String type) {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("Block type must not be blank");
        return with(Key.TYPE, type);
    }
    public BlockBuilder setProperties(MMDBlock.Properties properties) { return with(Key.PROPERTIES, properties); }
    public MMDBlock.Properties getProperties() { return value(Key.PROPERTIES, MMDBlock.Properties.DEFAULT); }
    private BlockBuilder edit(java.util.function.Function<MMDBlock.Properties, MMDBlock.Properties> change) {
        return setProperties(change.apply(getProperties()));
    }
    public BlockBuilder setStrength(float hardness, float resistance) {
        return edit(p -> new MMDBlock.Properties(hardness, resistance, p.friction(), p.speedFactor(), p.jumpFactor(),
                p.lightLevel(), p.requiresCorrectTool(), p.collision(), p.occlusion(), p.randomTicks(), p.replaceable(), p.sound(), p.mapColor()));
    }
    public BlockBuilder setHardness(float hardness) { return setStrength(hardness, getProperties().blastResistance()); }
    public BlockBuilder setBlastResistance(float resistance) { return setStrength(getProperties().hardness(), resistance); }
    public BlockBuilder setFriction(float friction) {
        return edit(p -> new MMDBlock.Properties(p.hardness(), p.blastResistance(), friction, p.speedFactor(), p.jumpFactor(),
                p.lightLevel(), p.requiresCorrectTool(), p.collision(), p.occlusion(), p.randomTicks(), p.replaceable(), p.sound(), p.mapColor()));
    }
    public BlockBuilder setSpeedFactor(float speed) {
        return edit(p -> new MMDBlock.Properties(p.hardness(), p.blastResistance(), p.friction(), speed, p.jumpFactor(),
                p.lightLevel(), p.requiresCorrectTool(), p.collision(), p.occlusion(), p.randomTicks(), p.replaceable(), p.sound(), p.mapColor()));
    }
    public BlockBuilder setJumpFactor(float jump) {
        return edit(p -> new MMDBlock.Properties(p.hardness(), p.blastResistance(), p.friction(), p.speedFactor(), jump,
                p.lightLevel(), p.requiresCorrectTool(), p.collision(), p.occlusion(), p.randomTicks(), p.replaceable(), p.sound(), p.mapColor()));
    }
    public BlockBuilder setLightLevel(int light) {
        return edit(p -> new MMDBlock.Properties(p.hardness(), p.blastResistance(), p.friction(), p.speedFactor(), p.jumpFactor(),
                light, p.requiresCorrectTool(), p.collision(), p.occlusion(), p.randomTicks(), p.replaceable(), p.sound(), p.mapColor()));
    }
    public BlockBuilder setRequiresCorrectToolForDrops(boolean required) {
        return edit(p -> new MMDBlock.Properties(p.hardness(), p.blastResistance(), p.friction(), p.speedFactor(), p.jumpFactor(),
                p.lightLevel(), required, p.collision(), p.occlusion(), p.randomTicks(), p.replaceable(), p.sound(), p.mapColor()));
    }
    public BlockBuilder setCollision(boolean collision) {
        return edit(p -> new MMDBlock.Properties(p.hardness(), p.blastResistance(), p.friction(), p.speedFactor(), p.jumpFactor(),
                p.lightLevel(), p.requiresCorrectTool(), collision, p.occlusion(), p.randomTicks(), p.replaceable(), p.sound(), p.mapColor()));
    }
    public BlockBuilder setOcclusion(boolean occlusion) {
        return edit(p -> new MMDBlock.Properties(p.hardness(), p.blastResistance(), p.friction(), p.speedFactor(), p.jumpFactor(),
                p.lightLevel(), p.requiresCorrectTool(), p.collision(), occlusion, p.randomTicks(), p.replaceable(), p.sound(), p.mapColor()));
    }
    public BlockBuilder setRandomTicks(boolean ticks) {
        return edit(p -> new MMDBlock.Properties(p.hardness(), p.blastResistance(), p.friction(), p.speedFactor(), p.jumpFactor(),
                p.lightLevel(), p.requiresCorrectTool(), p.collision(), p.occlusion(), ticks, p.replaceable(), p.sound(), p.mapColor()));
    }
    public BlockBuilder setReplaceable(boolean replaceable) {
        return edit(p -> new MMDBlock.Properties(p.hardness(), p.blastResistance(), p.friction(), p.speedFactor(), p.jumpFactor(),
                p.lightLevel(), p.requiresCorrectTool(), p.collision(), p.occlusion(), p.randomTicks(), replaceable, p.sound(), p.mapColor()));
    }
    public BlockBuilder setSound(String sound) {
        return edit(p -> new MMDBlock.Properties(p.hardness(), p.blastResistance(), p.friction(), p.speedFactor(), p.jumpFactor(),
                p.lightLevel(), p.requiresCorrectTool(), p.collision(), p.occlusion(), p.randomTicks(), p.replaceable(), sound, p.mapColor()));
    }
    public BlockBuilder setMapColor(String color) {
        return edit(p -> new MMDBlock.Properties(p.hardness(), p.blastResistance(), p.friction(), p.speedFactor(), p.jumpFactor(),
                p.lightLevel(), p.requiresCorrectTool(), p.collision(), p.occlusion(), p.randomTicks(), p.replaceable(), p.sound(), color));
    }
    public BlockBuilder setBlockTags(Set<ResourceLocation> tags) { return with(Key.TAGS, Set.copyOf(tags)); }
    @Override public BlockBuilder setEventMap(Map<String, ResourceLocation> events) {
        events.forEach((event, action) -> {
            if (event.isBlank()) throw new IllegalArgumentException("Event name must not be blank");
            Objects.requireNonNull(action);
        });
        return with(Key.EVENTS, Map.copyOf(events));
    }
    public BlockBuilder setHasItem(boolean enabled) { return with(Key.HAS_ITEM, enabled); }
    public BlockBuilder setItemTemplate(ItemBuilder item) { return with(Key.ITEM, item).setHasItem(true); }
    public MMDBlock buildFor(ResourceLocation material) { return forMaterial(material).build(); }
    @Override public MMDBlock build() {
        if (getTemplateName() == null) throw new IllegalStateException("Block template needs a name");
        if (getMaterialName() == null) throw new IllegalStateException("Block template must be bound to a material");
        var item = value(Key.HAS_ITEM, true) ? value(Key.ITEM, ItemBuilder.create())
                .setName(getTemplateName()).forMaterial(getMaterialName()).build() : null;
        return new MMDBlock(getRegistryName(), getTemplateName(), getMaterialName(), value(Key.PARENT, null),
                value(Key.TYPE, "block"), getProperties(), value(Key.TAGS, Set.of()), value(Key.EVENTS, Map.of()), item);
    }
}
