package zone.moddev.mc.lib.common.codecs;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.lib.api.MaterialExtensions;
import zone.moddev.mc.lib.common.types.*;

public final class MMDMaterialCodec {
    private MMDMaterialCodec() {}
    public static final Codec<MMDMaterial> MMD_MATERIAL_CODEC = forExtensions(MaterialExtensions.GLOBAL);
    public static Codec<MMDMaterial> forExtensions(MaterialExtensions extensions) {
        // Capture a consistent type catalog for each operation, rather than caching an early snapshot.
        return new Codec<>() {
            private Codec<MMDMaterial> schema() {
                Codec<MMDMaterial> fields = RecordCodecBuilder.create(i -> i.group(
                        ResourceLocation.CODEC.optionalFieldOf("name").forGetter(MMDMaterial::name),
                        ResourceLocation.CODEC.optionalFieldOf("parent").forGetter(MMDMaterial::parent),
                        MaterialProperties.MAP_CODEC.forGetter(MMDMaterial::properties),
                        MaterialSelection.mapCodec(extensions.snapshot()).forGetter(MMDMaterial::selection),
                        EventBindings.MAP_CODEC.forGetter(MMDMaterial::events),
                        MaterialRuntimeSettings.CODEC.optionalFieldOf("runtime", MaterialRuntimeSettings.DEFAULT).forGetter(MMDMaterial::runtime)
                ).apply(i, MMDMaterial::new));
                return fields.validate(material -> {
                    try {
                        extensions.events().validate(material.events());
                        if (material.selection().hasFluid()) extensions.fluidModule(material.selection().fluidModule());
                        return DataResult.success(material);
                    }
                    catch (IllegalArgumentException ex) { return DataResult.error(ex::getMessage); }
                });
            }
            @Override public <T> DataResult<Pair<MMDMaterial, T>> decode(DynamicOps<T> ops, T input) {
                return schema().decode(ops, input);
            }
            @Override public <T> DataResult<T> encode(MMDMaterial material, DynamicOps<T> ops, T prefix) {
                return schema().encode(material, ops, prefix);
            }
        };
    }
}
