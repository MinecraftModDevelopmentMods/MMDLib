package zone.moddev.mc.lib.common.builders;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import zone.moddev.mc.lib.common.types.*;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/** Applies standard or mod-supplied immutable templates to a material's selected forms. */
public final class MaterialContentPlanner {
    private final Map<MaterialPart, ItemBuilder> items;
    private final Map<MaterialPart, BlockBuilder> blocks;
    private final Map<MaterialPart, EntityBuilder> entities;
    private final FluidBuilder fluid;

    public MaterialContentPlanner() {
        var items = new EnumMap<MaterialPart, ItemBuilder>(MaterialPart.class);
        var blocks = new EnumMap<MaterialPart, BlockBuilder>(MaterialPart.class);
        var entities = new EnumMap<MaterialPart, EntityBuilder>(MaterialPart.class);
        for (MaterialPart part : MaterialPart.values()) {
            if (part.kind() == MaterialPart.Kind.ITEM) items.put(part, ItemBuilder.create().setType(part.id()));
            else blocks.put(part, BlockBuilder.create().setType(part.id()).setHasItem(part != MaterialPart.DOUBLE_SLAB));
            if (part.needsEntity()) entities.put(part, EntityBuilder.create().setFactory(part.templateId()));
        }
        this.items = Map.copyOf(items); this.blocks = Map.copyOf(blocks); this.entities = Map.copyOf(entities);
        this.fluid = FluidBuilder.create().setName("mmdlib:molten");
    }
    private MaterialContentPlanner(Map<MaterialPart, ItemBuilder> items, Map<MaterialPart, BlockBuilder> blocks,
                                   Map<MaterialPart, EntityBuilder> entities, FluidBuilder fluid) {
        this.items = Map.copyOf(items); this.blocks = Map.copyOf(blocks); this.entities = Map.copyOf(entities);
        this.fluid = Objects.requireNonNull(fluid);
    }
    public MaterialContentPlanner withItemTemplate(MaterialPart part, ItemBuilder template) {
        if (part.kind() != MaterialPart.Kind.ITEM) throw new IllegalArgumentException("Use the block template for " + part);
        var copy = new EnumMap<MaterialPart, ItemBuilder>(MaterialPart.class); copy.putAll(items);
        copy.put(part, Objects.requireNonNull(template));
        return new MaterialContentPlanner(copy, blocks, entities, fluid);
    }
    public MaterialContentPlanner withBlockTemplate(MaterialPart part, BlockBuilder template) {
        if (part.kind() == MaterialPart.Kind.ITEM) throw new IllegalArgumentException("Not a block part: " + part);
        var copy = new EnumMap<MaterialPart, BlockBuilder>(MaterialPart.class); copy.putAll(blocks);
        copy.put(part, Objects.requireNonNull(template));
        return new MaterialContentPlanner(items, copy, entities, fluid);
    }
    public MaterialContentPlanner withEntityTemplate(MaterialPart part, EntityBuilder template) {
        if (!part.needsEntity()) throw new IllegalArgumentException("No standard entity companion for " + part);
        var copy = new EnumMap<MaterialPart, EntityBuilder>(MaterialPart.class); copy.putAll(entities);
        copy.put(part, Objects.requireNonNull(template));
        return new MaterialContentPlanner(items, blocks, copy, fluid);
    }
    public MaterialContentPlanner withFluidTemplate(FluidBuilder template) {
        return new MaterialContentPlanner(items, blocks, entities, template);
    }
    public MaterialContentPlan plan(ResourceLocation material, MaterialParts selection) {
        return plan(material, selection, modId -> ModList.get().isLoaded(modId));
    }
    /** Explicit mod predicate supports deterministic loading/tests without starting NeoForge. */
    public MaterialContentPlan plan(ResourceLocation material, MaterialParts selection, Predicate<String> installed) {
        Objects.requireNonNull(material); Objects.requireNonNull(selection); Objects.requireNonNull(installed);
        var itemDefinitions = new EnumMap<MaterialPart, MMDItem>(MaterialPart.class);
        var blockDefinitions = new EnumMap<MaterialPart, MMDBlock>(MaterialPart.class);
        var entityDefinitions = new EnumMap<MaterialPart, MMDEntity>(MaterialPart.class);
        for (MaterialPart part : selection.parts().stream().sorted().toList()) {
            if (!part.available(installed)) continue;
            if (part.kind() == MaterialPart.Kind.ITEM) {
                itemDefinitions.put(part, items.get(part).setName(part.templateId()).forMaterial(material).build());
            } else {
                var block = blocks.get(part).setName(part.templateId()).forMaterial(material).build();
                blockDefinitions.put(part, block);
                if (block.item() != null) itemDefinitions.put(part, block.item());
            }
            if (part.needsEntity()) entityDefinitions.put(part,
                    entities.get(part).setName(part.templateId()).forMaterial(material).build());
        }
        var fluidDefinition = selection.hasFluid() ? fluid.forMaterial(material).build() : null;
        return new MaterialContentPlan(material, itemDefinitions, blockDefinitions, entityDefinitions, fluidDefinition);
    }
}
