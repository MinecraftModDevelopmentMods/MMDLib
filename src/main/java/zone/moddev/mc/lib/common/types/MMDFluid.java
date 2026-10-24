package zone.moddev.mc.lib.common.types;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Material-bound fluid family definition. No fluid, bucket, or liquid block is registered here. */
public record MMDFluid(ResourceLocation name, ResourceLocation template, ResourceLocation material,
        ResourceLocation parent, TypeProperties typeProperties, FlowProperties flowProperties,
        Appearance appearance, MMDBlock block, MMDItem bucket, Map<String, ResourceLocation> events) {
    public MMDFluid {
        Objects.requireNonNull(name); Objects.requireNonNull(template); Objects.requireNonNull(material);
        Objects.requireNonNull(typeProperties); Objects.requireNonNull(flowProperties); Objects.requireNonNull(appearance);
        events = Map.copyOf(events);
        if (events.keySet().stream().anyMatch(String::isBlank)) throw new IllegalArgumentException("Blank event name");
        if (block != null && (!block.name().equals(name) || !block.material().equals(material) || block.item() != null))
            throw new IllegalArgumentException("Liquid block must share the fluid ID/material and have no block item");
        if (bucket != null && (!bucket.name().equals(ResourceLocation.fromNamespaceAndPath(name.getNamespace(), name.getPath() + "_bucket")) || !bucket.material().equals(material)))
            throw new IllegalArgumentException("Bucket must share its fluid's material and use the bucket ID");
    }
    public ResourceLocation typeId() { return name; }
    public ResourceLocation sourceId() { return name; }
    public ResourceLocation flowingId() { return ResourceLocation.fromNamespaceAndPath(name.getNamespace(), "flowing_" + name.getPath()); }
    public ResourceLocation blockId() { return block == null ? null : name; }
    public ResourceLocation bucketId() { return ResourceLocation.fromNamespaceAndPath(name.getNamespace(), name.getPath() + "_bucket"); }
    private static final Codec<Double> NONNEGATIVE = Codec.DOUBLE.validate(v -> Double.isFinite(v) && v >= 0
            ? DataResult.success(v) : DataResult.error(() -> "Value must be finite and nonnegative"));
    private static final Codec<Float> NONNEGATIVE_FLOAT = Codec.FLOAT.validate(v -> Float.isFinite(v) && v >= 0
            ? DataResult.success(v) : DataResult.error(() -> "Value must be finite and nonnegative"));
    public record TypeProperties(int density, int viscosity, int temperature, int lightLevel, double motionScale, boolean canSwim, boolean canDrown, boolean canExtinguish, boolean supportsBoating, boolean canHydrate) {
        public static final TypeProperties DEFAULT = new TypeProperties(1000, 1000, 300, 0, 0.014, true, true, false, false, false);
        public TypeProperties {
            if (viscosity < 0 || temperature < 0 || lightLevel < 0 || lightLevel > 15
                    || !Double.isFinite(motionScale) || motionScale < 0)
                throw new IllegalArgumentException("Invalid fluid type properties");
        }
        public static final Codec<TypeProperties> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("density", 1000).forGetter(TypeProperties::density),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("viscosity", 1000).forGetter(TypeProperties::viscosity),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("temperature", 300).forGetter(TypeProperties::temperature),
                Codec.intRange(0, 15).optionalFieldOf("light_level", 0).forGetter(TypeProperties::lightLevel),
                NONNEGATIVE.optionalFieldOf("motion_scale", 0.014).forGetter(TypeProperties::motionScale),
                Codec.BOOL.optionalFieldOf("can_swim", true).forGetter(TypeProperties::canSwim),
                Codec.BOOL.optionalFieldOf("can_drown", true).forGetter(TypeProperties::canDrown),
                Codec.BOOL.optionalFieldOf("can_extinguish", false).forGetter(TypeProperties::canExtinguish),
                Codec.BOOL.optionalFieldOf("supports_boating", false).forGetter(TypeProperties::supportsBoating),
                Codec.BOOL.optionalFieldOf("can_hydrate", false).forGetter(TypeProperties::canHydrate)
        ).apply(i, TypeProperties::new));
    }
    public record FlowProperties(int slopeFindDistance, int levelDecreasePerBlock, int tickRate, float explosionResistance, boolean canConvertToSource) {
        public static final FlowProperties DEFAULT = new FlowProperties(4, 1, 5, 100F, false);
        public FlowProperties {
            if (slopeFindDistance < 0 || levelDecreasePerBlock < 1 || levelDecreasePerBlock > 8 || tickRate < 1
                    || !Float.isFinite(explosionResistance) || explosionResistance < 0)
                throw new IllegalArgumentException("Invalid fluid flow properties");
        }
        public static final Codec<FlowProperties> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("slope_find_distance", 4).forGetter(FlowProperties::slopeFindDistance),
                Codec.intRange(1, 8).optionalFieldOf("level_decrease_per_block", 1).forGetter(FlowProperties::levelDecreasePerBlock),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("tick_rate", 5).forGetter(FlowProperties::tickRate),
                NONNEGATIVE_FLOAT.optionalFieldOf("explosion_resistance", 100F).forGetter(FlowProperties::explosionResistance),
                Codec.BOOL.optionalFieldOf("can_convert_to_source", false).forGetter(FlowProperties::canConvertToSource)
        ).apply(i, FlowProperties::new));
    }
    public record Appearance(ResourceLocation still, ResourceLocation flowing, Optional<ResourceLocation> overlay, int tint) {
        public static final Appearance DEFAULT = new Appearance(ResourceLocation.parse("minecraft:block/water_still"),
                ResourceLocation.parse("minecraft:block/water_flow"), Optional.empty(), -1);
        public Appearance { Objects.requireNonNull(still); Objects.requireNonNull(flowing); Objects.requireNonNull(overlay); }
        public static final Codec<Appearance> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.optionalFieldOf("still", DEFAULT.still).forGetter(Appearance::still),
                ResourceLocation.CODEC.optionalFieldOf("flowing", DEFAULT.flowing).forGetter(Appearance::flowing),
                ResourceLocation.CODEC.optionalFieldOf("overlay").forGetter(Appearance::overlay),
                Codec.INT.optionalFieldOf("tint", -1).forGetter(Appearance::tint)
        ).apply(i, Appearance::new));
    }
}
