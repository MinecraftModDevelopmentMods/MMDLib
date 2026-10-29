package zone.moddev.mc.lib.common.builders;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.common.types.MMDBlock;

/**
 * Builds {@link zone.moddev.mc.lib.common.types.MMDBlock} definitions. Start from {@link
 * #create()}; every setter returns a new builder. A block is named {@code <material>_<template>}
 * unless {@link #setRegistryName} fixes its ID, and gets a block item unless {@link #setHasItem}
 * turns it off.
 */
public final class BlockBuilder extends Builder<MMDBlock, BlockBuilder> {
  /** The empty builder every chain starts from. */
  public static final BlockBuilder INSTANCE = new BlockBuilder(Map.of());

  private enum Key {
    NAME,
    REGISTRY_NAME,
    MATERIAL,
    PARENT,
    TYPE,
    PROPERTIES,
    TAGS,
    EVENTS,
    HAS_ITEM,
    ITEM
  }

  private final Map<Key, Object> values;

  private BlockBuilder(Map<Key, Object> values) {
    this.values = Map.copyOf(values);
  }

  /** The empty builder. */
  public static BlockBuilder create() {
    return INSTANCE;
  }

  @SuppressWarnings("unchecked")
  private <T> T value(Key key, T fallback) {
    return (T) values.getOrDefault(key, fallback);
  }

  private BlockBuilder with(Key key, Object value) {
    var copy = new EnumMap<Key, Object>(Key.class);
    copy.putAll(values);
    copy.put(key, Objects.requireNonNull(value, key.name()));
    return new BlockBuilder(copy);
  }

  @Override
  public BlockBuilder setName(ResourceLocation name) {
    return with(Key.NAME, name);
  }

  @Override
  public BlockBuilder setName(String name) {
    return setName(ResourceLocation.parse(name));
  }

  /** The template (part) name, or null if not set. */
  public ResourceLocation getTemplateName() {
    return value(Key.NAME, null);
  }

  /** The owning material, or null if not set. */
  public ResourceLocation getMaterialName() {
    return value(Key.MATERIAL, null);
  }

  /** Binds the block to {@code material}. */
  public BlockBuilder forMaterial(ResourceLocation material) {
    return with(Key.MATERIAL, material);
  }

  /** As {@link #forMaterial(ResourceLocation)}, parsing {@code material}. */
  public BlockBuilder forMaterial(String material) {
    return forMaterial(ResourceLocation.parse(material));
  }

  /** Fixed registry ID for a standalone block, instead of {@code <material>_<template>}. */
  public BlockBuilder setRegistryName(ResourceLocation id) {
    return with(Key.REGISTRY_NAME, id);
  }

  @Override
  public ResourceLocation getRegistryName() {
    if (values.containsKey(Key.REGISTRY_NAME)) return value(Key.REGISTRY_NAME, null);
    var material = getMaterialName();
    var template = getTemplateName();
    return material == null || template == null
        ? null
        : ResourceLocation.fromNamespaceAndPath(
            material.getNamespace(), material.getPath() + "_" + template.getPath());
  }

  @Override
  public BlockBuilder setParent(ResourceLocation parent) {
    return with(Key.PARENT, parent);
  }

  /**
   * Sets the type: the ID of the Java implementation that creates the block.
   *
   * @throws IllegalArgumentException if {@code type} is blank
   */
  public BlockBuilder setType(String type) {
    if (type == null || type.isBlank())
      throw new IllegalArgumentException("Block type must not be blank");
    return with(Key.TYPE, type);
  }

  /** Replaces all block properties at once. */
  public BlockBuilder setProperties(MMDBlock.Properties properties) {
    return with(Key.PROPERTIES, properties);
  }

  /** The block properties so far, defaults if none were set. */
  public MMDBlock.Properties getProperties() {
    return value(Key.PROPERTIES, MMDBlock.Properties.DEFAULT);
  }

  private BlockBuilder edit(
      java.util.function.Function<MMDBlock.Properties, MMDBlock.Properties> change) {
    return setProperties(change.apply(getProperties()));
  }

  /** Sets how long the block takes to break and how well it resists explosions. */
  public BlockBuilder setStrength(float hardness, float resistance) {
    return edit(
        p ->
            new MMDBlock.Properties(
                hardness,
                resistance,
                p.friction(),
                p.speedFactor(),
                p.jumpFactor(),
                p.lightLevel(),
                p.requiresCorrectTool(),
                p.collision(),
                p.occlusion(),
                p.randomTicks(),
                p.replaceable(),
                p.sound(),
                p.mapColor()));
  }

  /** Sets how long the block takes to break. */
  public BlockBuilder setHardness(float hardness) {
    return setStrength(hardness, getProperties().blastResistance());
  }

  /** Sets how well the block resists explosions. */
  public BlockBuilder setBlastResistance(float resistance) {
    return setStrength(getProperties().hardness(), resistance);
  }

  /** Sets how slippery the block is to walk on (0.6 is normal, ice is 0.98). */
  public BlockBuilder setFriction(float friction) {
    return edit(
        p ->
            new MMDBlock.Properties(
                p.hardness(),
                p.blastResistance(),
                friction,
                p.speedFactor(),
                p.jumpFactor(),
                p.lightLevel(),
                p.requiresCorrectTool(),
                p.collision(),
                p.occlusion(),
                p.randomTicks(),
                p.replaceable(),
                p.sound(),
                p.mapColor()));
  }

  /** Sets the walking-speed multiplier on the block (1 is normal, soul sand is 0.4). */
  public BlockBuilder setSpeedFactor(float speed) {
    return edit(
        p ->
            new MMDBlock.Properties(
                p.hardness(),
                p.blastResistance(),
                p.friction(),
                speed,
                p.jumpFactor(),
                p.lightLevel(),
                p.requiresCorrectTool(),
                p.collision(),
                p.occlusion(),
                p.randomTicks(),
                p.replaceable(),
                p.sound(),
                p.mapColor()));
  }

  /** Sets the jump-height multiplier on the block (1 is normal, honey is 0.5). */
  public BlockBuilder setJumpFactor(float jump) {
    return edit(
        p ->
            new MMDBlock.Properties(
                p.hardness(),
                p.blastResistance(),
                p.friction(),
                p.speedFactor(),
                jump,
                p.lightLevel(),
                p.requiresCorrectTool(),
                p.collision(),
                p.occlusion(),
                p.randomTicks(),
                p.replaceable(),
                p.sound(),
                p.mapColor()));
  }

  /** Sets the light the block emits, 0-15. */
  public BlockBuilder setLightLevel(int light) {
    return edit(
        p ->
            new MMDBlock.Properties(
                p.hardness(),
                p.blastResistance(),
                p.friction(),
                p.speedFactor(),
                p.jumpFactor(),
                light,
                p.requiresCorrectTool(),
                p.collision(),
                p.occlusion(),
                p.randomTicks(),
                p.replaceable(),
                p.sound(),
                p.mapColor()));
  }

  /** Sets whether the block drops only when broken with the right tool. */
  public BlockBuilder setRequiresCorrectToolForDrops(boolean required) {
    return edit(
        p ->
            new MMDBlock.Properties(
                p.hardness(),
                p.blastResistance(),
                p.friction(),
                p.speedFactor(),
                p.jumpFactor(),
                p.lightLevel(),
                required,
                p.collision(),
                p.occlusion(),
                p.randomTicks(),
                p.replaceable(),
                p.sound(),
                p.mapColor()));
  }

  /** Sets whether entities collide with the block. */
  public BlockBuilder setCollision(boolean collision) {
    return edit(
        p ->
            new MMDBlock.Properties(
                p.hardness(),
                p.blastResistance(),
                p.friction(),
                p.speedFactor(),
                p.jumpFactor(),
                p.lightLevel(),
                p.requiresCorrectTool(),
                collision,
                p.occlusion(),
                p.randomTicks(),
                p.replaceable(),
                p.sound(),
                p.mapColor()));
  }

  /** Sets whether the block hides the faces of blocks behind it. */
  public BlockBuilder setOcclusion(boolean occlusion) {
    return edit(
        p ->
            new MMDBlock.Properties(
                p.hardness(),
                p.blastResistance(),
                p.friction(),
                p.speedFactor(),
                p.jumpFactor(),
                p.lightLevel(),
                p.requiresCorrectTool(),
                p.collision(),
                occlusion,
                p.randomTicks(),
                p.replaceable(),
                p.sound(),
                p.mapColor()));
  }

  /** Sets whether the block receives random ticks. */
  public BlockBuilder setRandomTicks(boolean ticks) {
    return edit(
        p ->
            new MMDBlock.Properties(
                p.hardness(),
                p.blastResistance(),
                p.friction(),
                p.speedFactor(),
                p.jumpFactor(),
                p.lightLevel(),
                p.requiresCorrectTool(),
                p.collision(),
                p.occlusion(),
                ticks,
                p.replaceable(),
                p.sound(),
                p.mapColor()));
  }

  /** Sets whether placing another block replaces it, as with grass. */
  public BlockBuilder setReplaceable(boolean replaceable) {
    return edit(
        p ->
            new MMDBlock.Properties(
                p.hardness(),
                p.blastResistance(),
                p.friction(),
                p.speedFactor(),
                p.jumpFactor(),
                p.lightLevel(),
                p.requiresCorrectTool(),
                p.collision(),
                p.occlusion(),
                p.randomTicks(),
                replaceable,
                p.sound(),
                p.mapColor()));
  }

  /** Sets the sound type by name, e.g. {@code metal}. */
  public BlockBuilder setSound(String sound) {
    return edit(
        p ->
            new MMDBlock.Properties(
                p.hardness(),
                p.blastResistance(),
                p.friction(),
                p.speedFactor(),
                p.jumpFactor(),
                p.lightLevel(),
                p.requiresCorrectTool(),
                p.collision(),
                p.occlusion(),
                p.randomTicks(),
                p.replaceable(),
                sound,
                p.mapColor()));
  }

  /** Sets the map color by name, e.g. {@code color_red}. */
  public BlockBuilder setMapColor(String color) {
    return edit(
        p ->
            new MMDBlock.Properties(
                p.hardness(),
                p.blastResistance(),
                p.friction(),
                p.speedFactor(),
                p.jumpFactor(),
                p.lightLevel(),
                p.requiresCorrectTool(),
                p.collision(),
                p.occlusion(),
                p.randomTicks(),
                p.replaceable(),
                p.sound(),
                color));
  }

  /** Sets the block tags the generator writes for this block. */
  public BlockBuilder setBlockTags(Set<ResourceLocation> tags) {
    return with(Key.TAGS, Set.copyOf(tags));
  }

  @Override
  public BlockBuilder setEventMap(Map<String, ResourceLocation> events) {
    events.forEach(
        (event, action) -> {
          if (event.isBlank()) throw new IllegalArgumentException("Event name must not be blank");
          Objects.requireNonNull(action);
        });
    return with(Key.EVENTS, Map.copyOf(events));
  }

  /** Sets whether the block gets a block item. */
  public BlockBuilder setHasItem(boolean enabled) {
    return with(Key.HAS_ITEM, enabled);
  }

  /** Sets the template for the block's item, and turns the item on. */
  public BlockBuilder setItemTemplate(ItemBuilder item) {
    return with(Key.ITEM, item).setHasItem(true);
  }

  /** Binds the block to {@code material} and builds it. */
  public MMDBlock buildFor(ResourceLocation material) {
    return forMaterial(material).build();
  }

  @Override
  public MMDBlock build() {
    if (getTemplateName() == null) throw new IllegalStateException("Block template needs a name");
    if (getMaterialName() == null)
      throw new IllegalStateException("Block template must be bound to a material");
    var item =
        value(Key.HAS_ITEM, true)
            ? value(Key.ITEM, ItemBuilder.create())
                .setName(getTemplateName())
                .forMaterial(getMaterialName())
                .setRegistryName(getRegistryName())
                .build()
            : null;
    return new MMDBlock(
        getRegistryName(),
        getTemplateName(),
        getMaterialName(),
        value(Key.PARENT, null),
        value(Key.TYPE, "block"),
        getProperties(),
        value(Key.TAGS, Set.of()),
        value(Key.EVENTS, Map.of()),
        item);
  }
}
