package zone.moddev.mc.lib.common.types;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.lib.common.builders.MaterialBuilder;
import zone.moddev.mc.lib.common.codecs.BlockTemplate;
import zone.moddev.mc.lib.common.factories.MaterialFactory;
import zone.moddev.mc.lib.common.modules.DataModules;
import java.util.Map;
import java.util.Optional;

/**
 * A block with its own registry ID and no material, as MMDLib 1.0 allowed (e.g. the player-only plate
 * {@code mmdlib:human_detector}). It is built by a block module's registered factory, like a material block.
 * {@code factory_data} is merged over the module's; {@code template} replaces the module's template; {@code runtime}
 * supplies what a material normally would (block set type, sound, map color, base block).
 */
public record StandaloneBlock(ResourceLocation module, FactoryData factoryData, Optional<BlockTemplate> template,
                              MaterialRuntimeSettings runtime) {
    public static final Codec<StandaloneBlock> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("module").forGetter(StandaloneBlock::module),
            FactoryData.CODEC.optionalFieldOf("factory_data", FactoryData.EMPTY).forGetter(StandaloneBlock::factoryData),
            BlockTemplate.CODEC.optionalFieldOf("template").forGetter(StandaloneBlock::template),
            MaterialRuntimeSettings.CODEC.optionalFieldOf("runtime", MaterialRuntimeSettings.DEFAULT).forGetter(StandaloneBlock::runtime)
    ).apply(i, StandaloneBlock::new));

    /**
     * Plans the block as a one-block content plan. Its owner is a host material named after the block, carrying
     * {@code runtime}; the host has no parts or stats and is not a loaded material.
     */
    public MaterialFactory.Result resolve(ResourceLocation id, DataModules.Block data) {
        var host = MaterialBuilder.create().setName(id).setRuntime(runtime).build();
        var part = new MaterialPart(module, MaterialPart.Kind.BLOCK, data.requiredMod(), data.part(), data.factory(), merged(data.factoryData()));
        if (part.needsBlockEntity() || part.needsEntity()) throw new IllegalArgumentException("Standalone blocks cannot use a module with companions: " + module);
        var block = template.orElse(data.template()).toBuilder().setName(module).setType(data.factory().toString())
                .forMaterial(id).setRegistryName(id).build();
        return new MaterialFactory.Result(host, new MaterialContentPlan(id, block.item() == null ? Map.of() : Map.of(part, block.item()),
                Map.of(part, block), Map.of(), null));
    }
    private FactoryData merged(FactoryData base) {
        if (!factoryData.json().isJsonObject() || !base.json().isJsonObject())
            throw new IllegalArgumentException("Standalone factory_data must be an object: " + module);
        JsonObject result = base.json().getAsJsonObject();
        factoryData.json().getAsJsonObject().entrySet().forEach(entry -> result.add(entry.getKey(), entry.getValue()));
        return new FactoryData(result);
    }
}
