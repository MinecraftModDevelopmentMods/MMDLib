package zone.moddev.mc.lib.common.types;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumSet;
import java.util.Set;

public record MaterialSelection(Set<MaterialPart> items, Set<MaterialPart> blocks,
                                Set<MaterialPart> entities, boolean hasFluid) {
    public static final MaterialSelection DEFAULT = new MaterialSelection(
            PartSelection.ITEMS, PartSelection.BLOCKS, PartSelection.ENTITIES, true);
    public MaterialSelection {
        items = Set.copyOf(items); blocks = Set.copyOf(blocks); entities = Set.copyOf(entities);
        if (!PartSelection.ITEMS.containsAll(items) || !PartSelection.BLOCKS.containsAll(blocks)
                || !PartSelection.ENTITIES.containsAll(entities)) throw new IllegalArgumentException("Part is in the wrong section");
    }
    public static final MapCodec<MaterialSelection> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            PartSelection.codec(PartSelection.ITEMS).optionalFieldOf("items", PartSelection.ITEMS).forGetter(MaterialSelection::items),
            PartSelection.codec(PartSelection.BLOCKS).optionalFieldOf("blocks", PartSelection.BLOCKS).forGetter(MaterialSelection::blocks),
            PartSelection.codec(PartSelection.ENTITIES).optionalFieldOf("entities", PartSelection.ENTITIES).forGetter(MaterialSelection::entities),
            Codec.BOOL.optionalFieldOf("has_fluid", true).forGetter(MaterialSelection::hasFluid)
    ).apply(i, MaterialSelection::new));
    public MaterialParts parts() {
        var parts = EnumSet.noneOf(MaterialPart.class);
        parts.addAll(items); parts.addAll(blocks);
        // Ammunition needs its entity companion. Entity selection can additionally disable it.
        parts.removeIf(part -> part.needsEntity() && !entities.contains(part));
        return new MaterialParts(parts, hasFluid);
    }
    public static MaterialSelection fromParts(MaterialParts selection) {
        var items = EnumSet.noneOf(MaterialPart.class);
        var blocks = EnumSet.noneOf(MaterialPart.class);
        for (var part : selection.parts()) {
            if (part.kind() == MaterialPart.Kind.ITEM) items.add(part); else blocks.add(part);
        }
        return new MaterialSelection(items, blocks, PartSelection.ENTITIES, selection.hasFluid());
    }
}
