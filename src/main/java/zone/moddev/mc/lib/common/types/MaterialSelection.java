package zone.moddev.mc.lib.common.types;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import zone.moddev.mc.lib.api.MaterialExtensions;
import java.util.Set;

public record MaterialSelection(Set<MaterialPart> items, Set<MaterialPart> blocks,
        Set<MaterialPart> entities, boolean hasFluid, Set<net.minecraft.resources.ResourceLocation> customItems,
        Set<net.minecraft.resources.ResourceLocation> customBlocks, net.minecraft.resources.ResourceLocation fluidModule) {
    public static final MaterialSelection DEFAULT = new MaterialSelection(PartSelection.ITEMS, PartSelection.BLOCKS, PartSelection.ENTITIES, true);
    public MaterialSelection(Set<MaterialPart> items, Set<MaterialPart> blocks, Set<MaterialPart> entities, boolean hasFluid) {
        this(items, blocks, entities, hasFluid, Set.of(), Set.of());
    }
    public MaterialSelection(Set<MaterialPart> items, Set<MaterialPart> blocks, Set<MaterialPart> entities,
            boolean hasFluid, Set<net.minecraft.resources.ResourceLocation> customItems, Set<net.minecraft.resources.ResourceLocation> customBlocks) {
        this(items, blocks, entities, hasFluid, customItems, customBlocks, net.minecraft.resources.ResourceLocation.parse("mmdlib:molten"));
    }
    public MaterialSelection {
        items = Set.copyOf(items); blocks = Set.copyOf(blocks); entities = Set.copyOf(entities);
        java.util.Objects.requireNonNull(fluidModule);
        customItems = Set.copyOf(customItems); customBlocks = Set.copyOf(customBlocks);
        if (items.stream().anyMatch(p -> p.kind() != MaterialPart.Kind.ITEM)
                || blocks.stream().anyMatch(p -> p.kind() == MaterialPart.Kind.ITEM)
                || entities.stream().anyMatch(p -> !p.needsEntity())) throw new IllegalArgumentException("Part is in the wrong section");
    }
    public static MaterialSelection defaults(MaterialExtensions.Snapshot snapshot) {
        var registry = snapshot.parts();
        return new MaterialSelection(registry.items(), registry.blocks(), registry.entities(), true);
    }
    public static MaterialSelection currentDefaults() { return defaults(MaterialExtensions.GLOBAL.snapshot()); }
    public static final MapCodec<MaterialSelection> MAP_CODEC = mapCodec(MaterialExtensions.GLOBAL.snapshot());
    public static MapCodec<MaterialSelection> mapCodec(MaterialExtensions.Snapshot snapshot) {
        var registry = snapshot.parts();
        return RecordCodecBuilder.mapCodec(i -> i.group(
                PartSelection.codec(registry.items()).optionalFieldOf("items", registry.items()).forGetter(MaterialSelection::items),
                PartSelection.codec(registry.blocks()).optionalFieldOf("blocks", registry.blocks()).forGetter(MaterialSelection::blocks),
                PartSelection.codec(registry.entities()).optionalFieldOf("entities", registry.entities()).forGetter(MaterialSelection::entities),
                Codec.BOOL.optionalFieldOf("has_fluid", true).forGetter(MaterialSelection::hasFluid),
                net.minecraft.resources.ResourceLocation.CODEC.optionalFieldOf("fluid", net.minecraft.resources.ResourceLocation.parse("mmdlib:molten")).forGetter(MaterialSelection::fluidModule)
        ).apply(i, (items, blocks, entities, fluid, module) -> new MaterialSelection(items, blocks, entities,
                fluid, Set.of(), Set.of(), module)));
    }
    public MaterialParts parts() {
        var parts = new java.util.HashSet<MaterialPart>(); parts.addAll(items); parts.addAll(blocks);
        parts.removeIf(part -> part.needsEntity() && !entities.contains(part));
        return new MaterialParts(parts, hasFluid);
    }
    public static MaterialSelection fromParts(MaterialParts selection) {
        var items = new java.util.HashSet<MaterialPart>(); var blocks = new java.util.HashSet<MaterialPart>();
        for (var part : selection.parts()) if (part.kind() == MaterialPart.Kind.ITEM) items.add(part); else blocks.add(part);
        var entities = selection.parts().stream().filter(MaterialPart::needsEntity).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new MaterialSelection(items, blocks, entities, selection.hasFluid());
    }
}
