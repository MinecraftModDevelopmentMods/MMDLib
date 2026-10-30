package zone.moddev.mc.lib.common.types;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/**
 * A material's fluid family: the fluid type, its source and flowing fluids, its liquid block and
 * its bucket. Only a definition: nothing is registered here.
 *
 * @param name the fluid ID, also used for the fluid type, the source fluid and the liquid block
 * @param template the fluid module it was built from
 * @param material the owning material
 * @param parent an optional parent definition, may be null
 * @param typeProperties physical properties of the fluid type
 * @param flowProperties how the fluid spreads in the world
 * @param appearance textures and tint
 * @param block the liquid block, or null for none; it has no item
 * @param bucket the bucket item, or null for none; named {@code <name>_bucket}
 * @param events event bindings for the fluid
 * @param factory the fluid implementation ID, {@code mmdlib:flowing_fluid} by default
 * @param factoryData that implementation's settings
 */
public record MMDFluid(
    ResourceLocation name,
    ResourceLocation template,
    ResourceLocation material,
    ResourceLocation parent,
    TypeProperties typeProperties,
    FlowProperties flowProperties,
    Appearance appearance,
    MMDBlock block,
    MMDItem bucket,
    Map<String, ResourceLocation> events,
    ResourceLocation factory,
    FactoryData factoryData) {
  /** A fluid created by the default {@code mmdlib:flowing_fluid} implementation. */
  public MMDFluid(
      ResourceLocation name,
      ResourceLocation template,
      ResourceLocation material,
      ResourceLocation parent,
      TypeProperties typeProperties,
      FlowProperties flowProperties,
      Appearance appearance,
      MMDBlock block,
      MMDItem bucket,
      Map<String, ResourceLocation> events) {
    this(
        name,
        template,
        material,
        parent,
        typeProperties,
        flowProperties,
        appearance,
        block,
        bucket,
        events,
        ResourceLocation.parse("mmdlib:flowing_fluid"),
        FactoryData.EMPTY);
  }

  /**
   * Validates the definition and copies the event bindings.
   *
   * @throws IllegalArgumentException if an event name is blank; the liquid block does not share the
   *     fluid's ID and material or has an item; or the bucket does not share the material or is not
   *     named {@code <name>_bucket}
   */
  public MMDFluid {
    Objects.requireNonNull(factory);
    Objects.requireNonNull(factoryData);
    Objects.requireNonNull(name);
    Objects.requireNonNull(template);
    Objects.requireNonNull(material);
    Objects.requireNonNull(typeProperties);
    Objects.requireNonNull(flowProperties);
    Objects.requireNonNull(appearance);
    events = Map.copyOf(events);
    if (events.keySet().stream().anyMatch(String::isBlank))
      throw new IllegalArgumentException("Blank event name");
    if (block != null
        && (!block.name().equals(name)
            || !block.material().equals(material)
            || block.item() != null))
      throw new IllegalArgumentException(
          "Liquid block must share the fluid ID/material and have no block item");
    if (bucket != null
        && (!bucket
                .name()
                .equals(
                    ResourceLocation.fromNamespaceAndPath(
                        name.getNamespace(), name.getPath() + "_bucket"))
            || !bucket.material().equals(material)))
      throw new IllegalArgumentException(
          "Bucket must share its fluid's material and use the bucket ID");
  }

  /** The fluid type's ID (the same as {@link #name()}). */
  public ResourceLocation typeId() {
    return name;
  }

  /** The source fluid's ID (the same as {@link #name()}). */
  public ResourceLocation sourceId() {
    return name;
  }

  /** The flowing fluid's ID: {@code flowing_<name>}. */
  public ResourceLocation flowingId() {
    return ResourceLocation.fromNamespaceAndPath(name.getNamespace(), "flowing_" + name.getPath());
  }

  /** The liquid block's ID, or null when there is no block. */
  public ResourceLocation blockId() {
    return block == null ? null : name;
  }

  /** The bucket's ID: {@code <name>_bucket}. */
  public ResourceLocation bucketId() {
    return ResourceLocation.fromNamespaceAndPath(name.getNamespace(), name.getPath() + "_bucket");
  }

  private static final Codec<Double> NONNEGATIVE =
      Codec.DOUBLE.validate(
          v ->
              Double.isFinite(v) && v >= 0
                  ? DataResult.success(v)
                  : DataResult.error(() -> "Value must be finite and nonnegative"));
  private static final Codec<Float> NONNEGATIVE_FLOAT =
      Codec.FLOAT.validate(
          v ->
              Float.isFinite(v) && v >= 0
                  ? DataResult.success(v)
                  : DataResult.error(() -> "Value must be finite and nonnegative"));

  /**
   * The fluid type's physical properties, as in the {@code type} object of a fluid module.
   *
   * @param density relative density; lighter-than-air fluids are negative
   * @param viscosity how slowly it flows and how hard it is to move through
   * @param temperature in kelvin
   * @param lightLevel light emitted, 0-15
   * @param motionScale how strongly it pushes entities
   * @param canSwim whether entities can swim in it
   * @param canDrown whether entities can drown in it
   * @param canExtinguish whether it puts out burning entities
   * @param supportsBoating whether boats float on it
   * @param canHydrate whether it hydrates farmland and the like
   */
  public record TypeProperties(
      int density,
      int viscosity,
      int temperature,
      int lightLevel,
      double motionScale,
      boolean canSwim,
      boolean canDrown,
      boolean canExtinguish,
      boolean supportsBoating,
      boolean canHydrate) {
    public static final TypeProperties DEFAULT =
        new TypeProperties(1000, 1000, 300, 0, 0.014, true, true, false, false, false);

    /**
     * Validates the values.
     *
     * @throws IllegalArgumentException if viscosity or temperature is negative, the light level is
     *     outside 0-15, or the motion scale is negative or not finite
     */
    public TypeProperties {
      if (viscosity < 0
          || temperature < 0
          || lightLevel < 0
          || lightLevel > 15
          || !Double.isFinite(motionScale)
          || motionScale < 0) throw new IllegalArgumentException("Invalid fluid type properties");
    }

    public static final Codec<TypeProperties> CODEC =
        RecordCodecBuilder.create(
            i ->
                i.group(
                        Codec.INT
                            .optionalFieldOf("density", 1000)
                            .forGetter(TypeProperties::density),
                        Codec.intRange(0, Integer.MAX_VALUE)
                            .optionalFieldOf("viscosity", 1000)
                            .forGetter(TypeProperties::viscosity),
                        Codec.intRange(0, Integer.MAX_VALUE)
                            .optionalFieldOf("temperature", 300)
                            .forGetter(TypeProperties::temperature),
                        Codec.intRange(0, 15)
                            .optionalFieldOf("light_level", 0)
                            .forGetter(TypeProperties::lightLevel),
                        NONNEGATIVE
                            .optionalFieldOf("motion_scale", 0.014)
                            .forGetter(TypeProperties::motionScale),
                        Codec.BOOL
                            .optionalFieldOf("can_swim", true)
                            .forGetter(TypeProperties::canSwim),
                        Codec.BOOL
                            .optionalFieldOf("can_drown", true)
                            .forGetter(TypeProperties::canDrown),
                        Codec.BOOL
                            .optionalFieldOf("can_extinguish", false)
                            .forGetter(TypeProperties::canExtinguish),
                        Codec.BOOL
                            .optionalFieldOf("supports_boating", false)
                            .forGetter(TypeProperties::supportsBoating),
                        Codec.BOOL
                            .optionalFieldOf("can_hydrate", false)
                            .forGetter(TypeProperties::canHydrate))
                    .apply(i, TypeProperties::new));
  }

  /**
   * How the fluid spreads in the world, as in the {@code flow} object of a fluid module.
   *
   * @param slopeFindDistance how far the flowing fluid looks for a way down
   * @param levelDecreasePerBlock how much the level drops per block travelled (1-8)
   * @param tickRate ticks between flow updates
   * @param explosionResistance the liquid block's blast resistance
   * @param canConvertToSource whether two sources make a new source, as water does
   */
  public record FlowProperties(
      int slopeFindDistance,
      int levelDecreasePerBlock,
      int tickRate,
      float explosionResistance,
      boolean canConvertToSource) {
    public static final FlowProperties DEFAULT = new FlowProperties(4, 1, 5, 100F, false);

    /**
     * Validates the values.
     *
     * @throws IllegalArgumentException if the slope distance is negative, the level decrease is
     *     outside 1-8, the tick rate is not positive, or the explosion resistance is negative or
     *     not finite
     */
    public FlowProperties {
      if (slopeFindDistance < 0
          || levelDecreasePerBlock < 1
          || levelDecreasePerBlock > 8
          || tickRate < 1
          || !Float.isFinite(explosionResistance)
          || explosionResistance < 0)
        throw new IllegalArgumentException("Invalid fluid flow properties");
    }

    public static final Codec<FlowProperties> CODEC =
        RecordCodecBuilder.create(
            i ->
                i.group(
                        Codec.intRange(0, Integer.MAX_VALUE)
                            .optionalFieldOf("slope_find_distance", 4)
                            .forGetter(FlowProperties::slopeFindDistance),
                        Codec.intRange(1, 8)
                            .optionalFieldOf("level_decrease_per_block", 1)
                            .forGetter(FlowProperties::levelDecreasePerBlock),
                        Codec.intRange(1, Integer.MAX_VALUE)
                            .optionalFieldOf("tick_rate", 5)
                            .forGetter(FlowProperties::tickRate),
                        NONNEGATIVE_FLOAT
                            .optionalFieldOf("explosion_resistance", 100F)
                            .forGetter(FlowProperties::explosionResistance),
                        Codec.BOOL
                            .optionalFieldOf("can_convert_to_source", false)
                            .forGetter(FlowProperties::canConvertToSource))
                    .apply(i, FlowProperties::new));
  }

  /**
   * How the fluid looks, as in the {@code appearance} object of a fluid module.
   *
   * @param still the still texture
   * @param flowing the flowing texture
   * @param overlay the texture seen through the fluid from inside, if any
   * @param tint an ARGB tint multiplied over the textures; -1 for none
   */
  public record Appearance(
      ResourceLocation still,
      ResourceLocation flowing,
      Optional<ResourceLocation> overlay,
      int tint) {
    public static final Appearance DEFAULT =
        new Appearance(
            ResourceLocation.parse("minecraft:block/water_still"),
            ResourceLocation.parse("minecraft:block/water_flow"),
            Optional.empty(),
            -1);

    /** Requires the textures; {@code overlay} may be empty but not null. */
    public Appearance {
      Objects.requireNonNull(still);
      Objects.requireNonNull(flowing);
      Objects.requireNonNull(overlay);
    }

    public static final Codec<Appearance> CODEC =
        RecordCodecBuilder.create(
            i ->
                i.group(
                        ResourceLocation.CODEC
                            .optionalFieldOf("still", DEFAULT.still)
                            .forGetter(Appearance::still),
                        ResourceLocation.CODEC
                            .optionalFieldOf("flowing", DEFAULT.flowing)
                            .forGetter(Appearance::flowing),
                        ResourceLocation.CODEC
                            .optionalFieldOf("overlay")
                            .forGetter(Appearance::overlay),
                        Codec.INT.optionalFieldOf("tint", -1).forGetter(Appearance::tint))
                    .apply(i, Appearance::new));
  }
}
