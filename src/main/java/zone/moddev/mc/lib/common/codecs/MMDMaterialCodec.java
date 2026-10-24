package zone.moddev.mc.lib.common.codecs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.lib.common.types.*;
import java.util.Map;

public final class MMDMaterialCodec {
    private MMDMaterialCodec() {}
    private static final Codec<MMDMaterial> FIELDS = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("name").forGetter(MMDMaterial::name),
            ResourceLocation.CODEC.optionalFieldOf("parent").forGetter(MMDMaterial::parent),
            MaterialProperties.MAP_CODEC.forGetter(MMDMaterial::properties),
            MaterialSelection.MAP_CODEC.forGetter(MMDMaterial::selection),
            Codec.unboundedMap(Codec.STRING.validate(key -> key.isBlank() ? DataResult.error(() -> "Blank event name") : DataResult.success(key)), ResourceLocation.CODEC).optionalFieldOf("events", Map.of()).forGetter(MMDMaterial::events)
    ).apply(i, MMDMaterial::new));
    public static final Codec<MMDMaterial> MMD_MATERIAL_CODEC = FIELDS.validate(material ->
            material.events().keySet().stream().anyMatch(String::isBlank)
                    ? DataResult.error(() -> "Event name must not be blank") : DataResult.success(material));
}
