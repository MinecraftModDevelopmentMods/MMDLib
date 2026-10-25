package zone.moddev.mc.lib.common.types;

import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.Objects;

/** Definitions only; maps contain no live Minecraft objects. Fluid companions live in fluid(). */
public record MaterialContentPlan(ResourceLocation material, Map<MaterialPart, MMDItem> items,
        Map<MaterialPart, MMDBlock> blocks, Map<MaterialPart, MMDEntity> entities, MMDFluid fluid,
        Map<ResourceLocation, MMDItem> extraItems, Map<ResourceLocation, MMDBlock> extraBlocks,
        Map<MaterialPart, MMDBlockEntity> blockEntities) {
    public MaterialContentPlan(ResourceLocation material, Map<MaterialPart, MMDItem> items,
            Map<MaterialPart, MMDBlock> blocks, Map<MaterialPart, MMDEntity> entities, MMDFluid fluid) {
        this(material, items, blocks, entities, fluid, Map.of(), Map.of());
    }
    public MaterialContentPlan(ResourceLocation material, Map<MaterialPart, MMDItem> items,
            Map<MaterialPart, MMDBlock> blocks, Map<MaterialPart, MMDEntity> entities, MMDFluid fluid,
            Map<ResourceLocation, MMDItem> extraItems, Map<ResourceLocation, MMDBlock> extraBlocks) {
        this(material, items, blocks, entities, fluid, extraItems, extraBlocks, Map.of());
    }
    public MaterialContentPlan {
        Objects.requireNonNull(material);
        items = Map.copyOf(items); blocks = Map.copyOf(blocks); entities = Map.copyOf(entities);
        blockEntities = Map.copyOf(blockEntities);
        for (var entry : blockEntities.entrySet()) {
            var block = blocks.get(entry.getKey()); var entity = entry.getValue();
            if (block == null || !entity.block().equals(block.name()) || !entity.material().equals(material))
                throw new IllegalArgumentException("Block entity must belong to a selected block and material: " + entity.name());
        }
        extraItems = Map.copyOf(extraItems); extraBlocks = Map.copyOf(extraBlocks);
    }
    /** Unique definitions to publish, including compatibility views and fluid companions. */
    public Map<ResourceLocation, MMDItem> itemDefinitions() {
        var result = definitions(items.values(), extraItems.values(), MMDItem::name);
        if (fluid != null && fluid.bucket() != null) putDefinition(result, fluid.bucket().name(), fluid.bucket());
        return Map.copyOf(result);
    }
    public Map<ResourceLocation, MMDBlock> blockDefinitions() {
        var result = definitions(blocks.values(), extraBlocks.values(), MMDBlock::name);
        if (fluid != null && fluid.block() != null) putDefinition(result, fluid.block().name(), fluid.block());
        return Map.copyOf(result);
    }
    public Map<ResourceLocation, MMDBlockEntity> blockEntityDefinitions() {
        return Map.copyOf(definitions(blockEntities.values(), java.util.List.of(), MMDBlockEntity::name));
    }
    private static <T> Map<ResourceLocation, T> definitions(java.util.Collection<T> primary,
            java.util.Collection<T> compatibility, java.util.function.Function<T, ResourceLocation> name) {
        var result = new java.util.LinkedHashMap<ResourceLocation, T>();
        primary.forEach(value -> putDefinition(result, name.apply(value), value));
        compatibility.forEach(value -> putDefinition(result, name.apply(value), value));
        return result;
    }
    private static <T> void putDefinition(Map<ResourceLocation, T> result, ResourceLocation name, T definition) {
        var previous = result.putIfAbsent(name, definition);
        if (previous != null && !previous.equals(definition)) throw new IllegalArgumentException("Conflicting definitions: " + name);
    }

}
