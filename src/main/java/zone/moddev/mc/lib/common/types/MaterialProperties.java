package zone.moddev.mc.lib.common.types;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.Set;

/** Raw material stats; magic is affinity, not a preconverted game enchantability value. */
public record MaterialProperties(String type, int color, double hardness, double strength, double magic,
                                 double damage, Set<ResourceLocation> harvestTags, Map<String, Double> special) {
    public static final MaterialProperties DEFAULT = new MaterialProperties("generic", -1, 0, 0, 0, 0, Set.of(), Map.of());
    public MaterialProperties {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("Material type must not be blank");
        if (!Double.isFinite(hardness) || hardness < 0 || !Double.isFinite(strength) || strength < 0
                || !Double.isFinite(magic) || magic < 0 || !Double.isFinite(damage))
            throw new IllegalArgumentException("Material stats must be finite; hardness, strength, and magic must be nonnegative");
        harvestTags = Set.copyOf(harvestTags); special = Map.copyOf(special);
        if (special.entrySet().stream().anyMatch(e -> e.getKey().isBlank() || !Double.isFinite(e.getValue())))
            throw new IllegalArgumentException("Extra property names must be nonblank and values finite");
    }
    private static final Codec<Double> FINITE = Codec.DOUBLE.validate(v -> Double.isFinite(v)
            ? DataResult.success(v) : DataResult.error(() -> "Property must be finite"));
    private static final Codec<Double> NONNEGATIVE = FINITE.validate(v -> v >= 0
            ? DataResult.success(v) : DataResult.error(() -> "Property must be nonnegative"));
    private static final Codec<String> NONBLANK = Codec.STRING.validate(v -> !v.isBlank()
            ? DataResult.success(v) : DataResult.error(() -> "Name must not be blank"));
    public static final MapCodec<MaterialProperties> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NONBLANK.optionalFieldOf("type", "generic").forGetter(MaterialProperties::type),
            Codec.INT.optionalFieldOf("color", -1).forGetter(MaterialProperties::color),
            NONNEGATIVE.optionalFieldOf("hardness", 0D).forGetter(MaterialProperties::hardness),
            NONNEGATIVE.optionalFieldOf("strength", 0D).forGetter(MaterialProperties::strength),
            NONNEGATIVE.optionalFieldOf("magic", 0D).forGetter(MaterialProperties::magic),
            FINITE.optionalFieldOf("damage", 0D).forGetter(MaterialProperties::damage),
            ResourceLocation.CODEC.listOf().xmap(Set::copyOf, tags -> tags.stream().sorted().toList())
                    .optionalFieldOf("harvest_tags", Set.of()).forGetter(MaterialProperties::harvestTags),
            Codec.unboundedMap(NONBLANK, FINITE).optionalFieldOf("special", Map.of()).forGetter(MaterialProperties::special)
    ).apply(i, MaterialProperties::new));
}
