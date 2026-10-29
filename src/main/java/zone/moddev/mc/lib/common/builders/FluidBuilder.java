package zone.moddev.mc.lib.common.builders;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.common.types.MMDFluid;

/**
 * Builds {@link zone.moddev.mc.lib.common.types.MMDFluid} definitions: a material's fluid, with an
 * optional liquid block and bucket. Start from {@link #create()}; every setter returns a new
 * builder. The {@code set...} property methods change one field of {@link MMDFluid.TypeProperties},
 * {@link MMDFluid.FlowProperties} or {@link MMDFluid.Appearance}.
 */
public final class FluidBuilder extends Builder<MMDFluid, FluidBuilder> {
  /** The empty builder every chain starts from. */
  public static final FluidBuilder INSTANCE = new FluidBuilder(Map.of());

  private enum Key {
    NAME,
    MATERIAL,
    PARENT,
    TYPE,
    FLOW,
    APPEARANCE,
    HAS_BLOCK,
    BLOCK,
    HAS_BUCKET,
    BUCKET,
    EVENTS,
    FACTORY,
    FACTORY_DATA
  }

  private final Map<Key, Object> values;

  private FluidBuilder(Map<Key, Object> values) {
    this.values = Map.copyOf(values);
  }

  /** The empty builder. */
  public static FluidBuilder create() {
    return INSTANCE;
  }

  @SuppressWarnings("unchecked")
  private <T> T value(Key key, T fallback) {
    return (T) values.getOrDefault(key, fallback);
  }

  private FluidBuilder with(Key key, Object value) {
    var copy = new EnumMap<Key, Object>(Key.class);
    copy.putAll(values);
    copy.put(key, Objects.requireNonNull(value));
    return new FluidBuilder(copy);
  }

  @Override
  public FluidBuilder setName(ResourceLocation name) {
    return with(Key.NAME, name);
  }

  @Override
  public FluidBuilder setName(String name) {
    return setName(ResourceLocation.parse(name));
  }

  /** The template (fluid module) name, or null if not set. */
  public ResourceLocation getTemplateName() {
    return value(Key.NAME, null);
  }

  /** The owning material, or null if not set. */
  public ResourceLocation getMaterialName() {
    return value(Key.MATERIAL, null);
  }

  /** Binds the fluid to {@code material}. */
  public FluidBuilder forMaterial(ResourceLocation material) {
    return with(Key.MATERIAL, material);
  }

  /** As {@link #forMaterial(ResourceLocation)}, parsing {@code material}. */
  public FluidBuilder forMaterial(String material) {
    return forMaterial(ResourceLocation.parse(material));
  }

  @Override
  public ResourceLocation getRegistryName() {
    var template = getTemplateName();
    var material = getMaterialName();
    return template == null || material == null
        ? null
        : ResourceLocation.fromNamespaceAndPath(
            material.getNamespace(), material.getPath() + "_" + template.getPath());
  }

  @Override
  public FluidBuilder setParent(ResourceLocation parent) {
    return with(Key.PARENT, parent);
  }

  /** Sets the ID of the fluid implementation, {@code mmdlib:flowing_fluid} by default. */
  public FluidBuilder setFactory(ResourceLocation factory) {
    return with(Key.FACTORY, factory);
  }

  /** Sets that implementation's settings. */
  public FluidBuilder setFactoryData(zone.moddev.mc.lib.common.types.FactoryData data) {
    return with(Key.FACTORY_DATA, data);
  }

  /** Replaces all fluid-type properties. */
  public FluidBuilder setTypeProperties(MMDFluid.TypeProperties properties) {
    return with(Key.TYPE, properties);
  }

  /** Replaces all flow properties. */
  public FluidBuilder setFlowProperties(MMDFluid.FlowProperties properties) {
    return with(Key.FLOW, properties);
  }

  /** Replaces textures and tint. */
  public FluidBuilder setAppearance(MMDFluid.Appearance appearance) {
    return with(Key.APPEARANCE, appearance);
  }

  /** The fluid-type properties so far. */
  public MMDFluid.TypeProperties getTypeProperties() {
    return value(Key.TYPE, MMDFluid.TypeProperties.DEFAULT);
  }

  /** The flow properties so far. */
  public MMDFluid.FlowProperties getFlowProperties() {
    return value(Key.FLOW, MMDFluid.FlowProperties.DEFAULT);
  }

  /** The textures and tint so far. */
  public MMDFluid.Appearance getAppearance() {
    return value(Key.APPEARANCE, MMDFluid.Appearance.DEFAULT);
  }

  /** Sets the density; negative values make a lighter-than-air fluid. */
  public FluidBuilder setDensity(int value) {
    var p = getTypeProperties();
    return setTypeProperties(
        new MMDFluid.TypeProperties(
            value,
            p.viscosity(),
            p.temperature(),
            p.lightLevel(),
            p.motionScale(),
            p.canSwim(),
            p.canDrown(),
            p.canExtinguish(),
            p.supportsBoating(),
            p.canHydrate()));
  }

  /** Sets the viscosity: how slowly it flows (water 1000, lava 6000). */
  public FluidBuilder setViscosity(int value) {
    var p = getTypeProperties();
    return setTypeProperties(
        new MMDFluid.TypeProperties(
            p.density(),
            value,
            p.temperature(),
            p.lightLevel(),
            p.motionScale(),
            p.canSwim(),
            p.canDrown(),
            p.canExtinguish(),
            p.supportsBoating(),
            p.canHydrate()));
  }

  /** Sets the temperature in kelvin (water 300, lava 1300). */
  public FluidBuilder setTemperature(int value) {
    var p = getTypeProperties();
    return setTypeProperties(
        new MMDFluid.TypeProperties(
            p.density(),
            p.viscosity(),
            value,
            p.lightLevel(),
            p.motionScale(),
            p.canSwim(),
            p.canDrown(),
            p.canExtinguish(),
            p.supportsBoating(),
            p.canHydrate()));
  }

  /** Sets the light the fluid emits, 0-15. */
  public FluidBuilder setLightLevel(int value) {
    var p = getTypeProperties();
    return setTypeProperties(
        new MMDFluid.TypeProperties(
            p.density(),
            p.viscosity(),
            p.temperature(),
            value,
            p.motionScale(),
            p.canSwim(),
            p.canDrown(),
            p.canExtinguish(),
            p.supportsBoating(),
            p.canHydrate()));
  }

  /** Sets how strongly the fluid pushes entities. */
  public FluidBuilder setMotionScale(double value) {
    var p = getTypeProperties();
    return setTypeProperties(
        new MMDFluid.TypeProperties(
            p.density(),
            p.viscosity(),
            p.temperature(),
            p.lightLevel(),
            value,
            p.canSwim(),
            p.canDrown(),
            p.canExtinguish(),
            p.supportsBoating(),
            p.canHydrate()));
  }

  /** Sets whether entities can swim in the fluid. */
  public FluidBuilder setCanSwim(boolean value) {
    var p = getTypeProperties();
    return setTypeProperties(
        new MMDFluid.TypeProperties(
            p.density(),
            p.viscosity(),
            p.temperature(),
            p.lightLevel(),
            p.motionScale(),
            value,
            p.canDrown(),
            p.canExtinguish(),
            p.supportsBoating(),
            p.canHydrate()));
  }

  /** Sets whether entities can drown in the fluid. */
  public FluidBuilder setCanDrown(boolean value) {
    var p = getTypeProperties();
    return setTypeProperties(
        new MMDFluid.TypeProperties(
            p.density(),
            p.viscosity(),
            p.temperature(),
            p.lightLevel(),
            p.motionScale(),
            p.canSwim(),
            value,
            p.canExtinguish(),
            p.supportsBoating(),
            p.canHydrate()));
  }

  /** Sets whether the fluid puts out burning entities. */
  public FluidBuilder setCanExtinguish(boolean value) {
    var p = getTypeProperties();
    return setTypeProperties(
        new MMDFluid.TypeProperties(
            p.density(),
            p.viscosity(),
            p.temperature(),
            p.lightLevel(),
            p.motionScale(),
            p.canSwim(),
            p.canDrown(),
            value,
            p.supportsBoating(),
            p.canHydrate()));
  }

  /** Sets whether boats float on the fluid. */
  public FluidBuilder setSupportsBoating(boolean value) {
    var p = getTypeProperties();
    return setTypeProperties(
        new MMDFluid.TypeProperties(
            p.density(),
            p.viscosity(),
            p.temperature(),
            p.lightLevel(),
            p.motionScale(),
            p.canSwim(),
            p.canDrown(),
            p.canExtinguish(),
            value,
            p.canHydrate()));
  }

  /** Sets whether the fluid hydrates farmland and the like. */
  public FluidBuilder setCanHydrate(boolean value) {
    var p = getTypeProperties();
    return setTypeProperties(
        new MMDFluid.TypeProperties(
            p.density(),
            p.viscosity(),
            p.temperature(),
            p.lightLevel(),
            p.motionScale(),
            p.canSwim(),
            p.canDrown(),
            p.canExtinguish(),
            p.supportsBoating(),
            value));
  }

  /** Sets how far flowing fluid looks for a way down (water 4, lava 2 in the Overworld). */
  public FluidBuilder setSlopeFindDistance(int value) {
    var p = getFlowProperties();
    return setFlowProperties(
        new MMDFluid.FlowProperties(
            value,
            p.levelDecreasePerBlock(),
            p.tickRate(),
            p.explosionResistance(),
            p.canConvertToSource()));
  }

  /** Sets how much the level drops per block travelled, 1-8 (water 1, lava 2). */
  public FluidBuilder setLevelDecreasePerBlock(int value) {
    var p = getFlowProperties();
    return setFlowProperties(
        new MMDFluid.FlowProperties(
            p.slopeFindDistance(),
            value,
            p.tickRate(),
            p.explosionResistance(),
            p.canConvertToSource()));
  }

  /** Sets the ticks between flow updates (water 5, lava 30). */
  public FluidBuilder setTickRate(int value) {
    var p = getFlowProperties();
    return setFlowProperties(
        new MMDFluid.FlowProperties(
            p.slopeFindDistance(),
            p.levelDecreasePerBlock(),
            value,
            p.explosionResistance(),
            p.canConvertToSource()));
  }

  /** Sets the liquid block's blast resistance. */
  public FluidBuilder setExplosionResistance(float value) {
    var p = getFlowProperties();
    return setFlowProperties(
        new MMDFluid.FlowProperties(
            p.slopeFindDistance(),
            p.levelDecreasePerBlock(),
            p.tickRate(),
            value,
            p.canConvertToSource()));
  }

  /** Sets whether two sources next to flowing fluid create a new source, as water does. */
  public FluidBuilder setCanConvertToSource(boolean value) {
    var p = getFlowProperties();
    return setFlowProperties(
        new MMDFluid.FlowProperties(
            p.slopeFindDistance(),
            p.levelDecreasePerBlock(),
            p.tickRate(),
            p.explosionResistance(),
            value));
  }

  /** Sets the still and flowing textures. */
  public FluidBuilder setTextures(ResourceLocation still, ResourceLocation flowing) {
    var p = getAppearance();
    return setAppearance(new MMDFluid.Appearance(still, flowing, p.overlay(), p.tint()));
  }

  /** Sets the texture seen through the fluid from inside, if any. */
  public FluidBuilder setOverlay(Optional<ResourceLocation> overlay) {
    var p = getAppearance();
    return setAppearance(new MMDFluid.Appearance(p.still(), p.flowing(), overlay, p.tint()));
  }

  /** Sets the ARGB tint multiplied over the textures; -1 for none. */
  public FluidBuilder setTintColor(int argb) {
    var p = getAppearance();
    return setAppearance(new MMDFluid.Appearance(p.still(), p.flowing(), p.overlay(), argb));
  }

  /** Sets whether the fluid gets a liquid block, so it can be placed in the world. */
  public FluidBuilder setHasBlock(boolean enabled) {
    return with(Key.HAS_BLOCK, enabled);
  }

  /** Sets the liquid block's template, and turns the block on. */
  public FluidBuilder setBlockTemplate(BlockBuilder block) {
    return with(Key.BLOCK, block).setHasBlock(true);
  }

  /** Sets whether the fluid gets a bucket. */
  public FluidBuilder setHasBucket(boolean enabled) {
    return with(Key.HAS_BUCKET, enabled);
  }

  /** Sets the bucket's template, and turns the bucket on. */
  public FluidBuilder setBucketTemplate(ItemBuilder bucket) {
    return with(Key.BUCKET, bucket).setHasBucket(true);
  }

  @Override
  public FluidBuilder setEventMap(Map<String, ResourceLocation> events) {
    events.forEach(
        (event, action) -> {
          if (event.isBlank()) throw new IllegalArgumentException("Blank event name");
          Objects.requireNonNull(action);
        });
    return with(Key.EVENTS, Map.copyOf(events));
  }

  /** Binds the fluid to {@code material} and builds it. */
  public MMDFluid buildFor(ResourceLocation material) {
    return forMaterial(material).build();
  }

  @Override
  public MMDFluid build() {
    if (getTemplateName() == null) throw new IllegalStateException("Fluid template needs a name");
    if (getMaterialName() == null)
      throw new IllegalStateException("Fluid template must be bound to a material");
    var block =
        value(Key.HAS_BLOCK, true)
            ? value(
                    Key.BLOCK,
                    BlockBuilder.create().setHardness(100).setCollision(false).setOcclusion(false))
                .setName(getTemplateName())
                .forMaterial(getMaterialName())
                .setHasItem(false)
                .build()
            : null;
    var bucketName =
        ResourceLocation.fromNamespaceAndPath(
            getTemplateName().getNamespace(), getTemplateName().getPath() + "_bucket");
    var bucket =
        value(Key.HAS_BUCKET, true)
            ? value(Key.BUCKET, ItemBuilder.create())
                .setName(bucketName)
                .forMaterial(getMaterialName())
                .setMaxStackSize(1)
                .build()
            : null;
    return new MMDFluid(
        getRegistryName(),
        getTemplateName(),
        getMaterialName(),
        value(Key.PARENT, null),
        getTypeProperties(),
        getFlowProperties(),
        getAppearance(),
        block,
        bucket,
        value(Key.EVENTS, Map.of()),
        value(Key.FACTORY, ResourceLocation.parse("mmdlib:flowing_fluid")),
        value(Key.FACTORY_DATA, zone.moddev.mc.lib.common.types.FactoryData.EMPTY));
  }
}
