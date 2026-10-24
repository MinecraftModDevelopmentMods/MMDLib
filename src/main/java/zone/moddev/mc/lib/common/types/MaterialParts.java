package zone.moddev.mc.lib.common.types;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
//import java.util.List;
import java.util.Set;

/** Default to all available standard parts and a fluid family; a material can restrict either. */
public record MaterialParts(Set<MaterialPart> parts, boolean hasFluid) {
    public static final MaterialParts DEFAULT = new MaterialParts(Set.of(MaterialPart.values()), true);
    public MaterialParts { parts = Set.copyOf(parts); }
    private static final Codec<Set<MaterialPart>> PARTS = MaterialPart.CODEC.listOf().comapFlatMap(values -> {
        Set<MaterialPart> unique = Set.copyOf(values);
        return unique.size() == values.size() ? DataResult.success(unique)
                : DataResult.error(() -> "Duplicate material part (including aliases)");
    }, values -> values.stream().sorted().toList());
    public static final Codec<MaterialParts> CODEC = RecordCodecBuilder.create(i -> i.group(
            PARTS.optionalFieldOf("parts", DEFAULT.parts).forGetter(MaterialParts::parts),
            Codec.BOOL.optionalFieldOf("has_fluid", true).forGetter(MaterialParts::hasFluid)
    ).apply(i, MaterialParts::new));
}
