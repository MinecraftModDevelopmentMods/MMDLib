package zone.moddev.mc.lib.common.types;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import zone.moddev.mc.lib.api.MaterialExtensions;
import java.util.EnumSet;
import java.util.Set;

public record MaterialSelection(Set<MaterialPart> items, Set<MaterialPart> blocks,
        Set<MaterialPart> entities, boolean hasFluid, Set<net.minecraft.resources.ResourceLocation> customItems,
        Set<net.minecraft.resources.ResourceLocation> customBlocks) {
    public static final MaterialSelection DEFAULT = new MaterialSelection(PartSelection.ITEMS, PartSelection.BLOCKS, PartSelection.ENTITIES, true);
    public MaterialSelection(Set<MaterialPart> items, Set<MaterialPart> blocks, Set<MaterialPart> entities, boolean hasFluid) {
        this(items, blocks, entities, hasFluid, Set.of(), Set.of());
    }
    public MaterialSelection {
        items = Set.copyOf(items); blocks = Set.copyOf(blocks); entities = Set.copyOf(entities);
        customItems = Set.copyOf(customItems); customBlocks = Set.copyOf(customBlocks);
        if (!PartSelection.ITEMS.containsAll(items) || !PartSelection.BLOCKS.containsAll(blocks)
                || !PartSelection.ENTITIES.containsAll(entities)) throw new IllegalArgumentException("Part is in the wrong section");
    }
    public static MaterialSelection defaults(MaterialExtensions.Snapshot snapshot) {
        return new MaterialSelection(PartSelection.ITEMS, PartSelection.BLOCKS, PartSelection.ENTITIES, true,
                snapshot.items().keySet(), snapshot.blocks().keySet());
    }
    public static MaterialSelection currentDefaults() { return defaults(MaterialExtensions.GLOBAL.snapshot()); }
    public static final MapCodec<MaterialSelection> MAP_CODEC = mapCodec(new MaterialExtensions.Snapshot(java.util.Map.of(), java.util.Map.of()));
    public static MapCodec<MaterialSelection> mapCodec(MaterialExtensions.Snapshot snapshot) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                TypeSelection.codec(PartSelection.ITEMS, snapshot.items().keySet()).optionalFieldOf("items",
                        new TypeSelection(PartSelection.ITEMS, snapshot.items().keySet()))
                        .forGetter((MaterialSelection s) -> new TypeSelection(s.items, s.customItems)),
                TypeSelection.codec(PartSelection.BLOCKS, snapshot.blocks().keySet()).optionalFieldOf("blocks",
                        new TypeSelection(PartSelection.BLOCKS, snapshot.blocks().keySet()))
                        .forGetter((MaterialSelection s) -> new TypeSelection(s.blocks, s.customBlocks)),
                PartSelection.codec(PartSelection.ENTITIES).optionalFieldOf("entities", PartSelection.ENTITIES).forGetter(MaterialSelection::entities),
                Codec.BOOL.optionalFieldOf("has_fluid", true).forGetter(MaterialSelection::hasFluid)
        ).apply(i, (items, blocks, entities, fluid) -> new MaterialSelection(items.standard(), blocks.standard(), entities,
                fluid, items.custom(), blocks.custom())));
    }
    public MaterialParts parts() {
        var parts = EnumSet.noneOf(MaterialPart.class); parts.addAll(items); parts.addAll(blocks);
        parts.removeIf(part -> part.needsEntity() && !entities.contains(part));
        return new MaterialParts(parts, hasFluid);
    }
    public static MaterialSelection fromParts(MaterialParts selection) {
        var items = EnumSet.noneOf(MaterialPart.class); var blocks = EnumSet.noneOf(MaterialPart.class);
        for (var part : selection.parts()) if (part.kind() == MaterialPart.Kind.ITEM) items.add(part); else blocks.add(part);
        return new MaterialSelection(items, blocks, PartSelection.ENTITIES, selection.hasFluid());
    }
}
