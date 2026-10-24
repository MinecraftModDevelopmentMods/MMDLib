package zone.moddev.mc.lib.common.types;

import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.Objects;

/** Definitions only; maps contain no live Minecraft objects. Fluid companions live in fluid(). */
public record MaterialContentPlan(ResourceLocation material, Map<MaterialPart, MMDItem> items,
        Map<MaterialPart, MMDBlock> blocks, Map<MaterialPart, MMDEntity> entities, MMDFluid fluid,
        Map<ResourceLocation, MMDItem> extraItems, Map<ResourceLocation, MMDBlock> extraBlocks) {
    public MaterialContentPlan(ResourceLocation material, Map<MaterialPart, MMDItem> items,
            Map<MaterialPart, MMDBlock> blocks, Map<MaterialPart, MMDEntity> entities, MMDFluid fluid) {
        this(material, items, blocks, entities, fluid, Map.of(), Map.of());
    }
    public MaterialContentPlan {
        Objects.requireNonNull(material);
        items = Map.copyOf(items); blocks = Map.copyOf(blocks); entities = Map.copyOf(entities);
        extraItems = Map.copyOf(extraItems); extraBlocks = Map.copyOf(extraBlocks);
    }
}
