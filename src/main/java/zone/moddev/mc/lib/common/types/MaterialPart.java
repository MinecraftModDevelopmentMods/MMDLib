package zone.moddev.mc.lib.common.types;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceLocation;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

/** Standard material forms; Java implementations are selected later by content factories. */
public enum MaterialPart {
    ARROW, AXE, BLEND, BOOTS, BOLT, BOW, CHESTPLATE, CRACKHAMMER, CROSSBOW, FISHING_ROD, GEAR,
    HELMET, HOE, HORSE_ARMOR, INGOT, LEGGINGS, NUGGET, PICKAXE, POWDER, ROD, SHEARS, SHIELD,
    SHOVEL, SMALLBLEND, SMALLPOWDER, SWORD, GEM, SCYTHE,
    BARS(Kind.BLOCK), BLOCK(Kind.BLOCK), BOOKSHELF(Kind.BLOCK), BUTTON(Kind.BLOCK),
    DOUBLE_SLAB(Kind.BLOCK), FLOWER_POT(Kind.BLOCK), LADDER(Kind.BLOCK), LEVER(Kind.BLOCK),
    PLATE(Kind.BLOCK), PRESSURE_PLATE(Kind.BLOCK), STAIRS(Kind.BLOCK), TRAPDOOR(Kind.BLOCK),
    TRIPWIRE_HOOK(Kind.BLOCK), WALL(Kind.BLOCK), FENCE(Kind.BLOCK), FENCE_GATE(Kind.BLOCK),
    ANVIL(Kind.ITEM_BLOCK), DOOR(Kind.ITEM_BLOCK), SLAB(Kind.ITEM_BLOCK),
    CRYSTAL(Kind.ITEM, "mekanism"), SHARD(Kind.ITEM, "mekanism"), CLUMP(Kind.ITEM, "mekanism"),
    POWDER_DIRTY(Kind.ITEM, "mekanism"),
    ORE(Kind.BLOCK), ENDORE(Kind.BLOCK), NETHERORE(Kind.BLOCK), MEK_CRYSTAL(Kind.BLOCK, "mekanism");

    public enum Kind { ITEM, BLOCK, ITEM_BLOCK }
    private final Kind kind;
    private final String requiredMod;
    MaterialPart() { this(Kind.ITEM, null); }
    MaterialPart(Kind kind) { this(kind, null); }
    MaterialPart(Kind kind, String requiredMod) { this.kind = kind; this.requiredMod = requiredMod; }
    public Kind kind() { return kind; }
    public Optional<String> requiredMod() { return Optional.ofNullable(requiredMod); }
    public boolean available(Predicate<String> installed) { return requiredMod == null || installed.test(requiredMod); }
    public boolean needsEntity() { return this == ARROW || this == BOLT; }
    public String id() { return this == POWDER ? "dust" : name().toLowerCase(Locale.ROOT); }
    public ResourceLocation templateId() { return ResourceLocation.fromNamespaceAndPath("mmdlib", id()); }
    public static final Codec<MaterialPart> CODEC = Codec.STRING.comapFlatMap(name -> {
        String normalized = switch (name) {
            case "powder" -> "dust";
            case "end_ore" -> "endore";
            case "nether_ore" -> "netherore";
            default -> name;
        };
        for (MaterialPart part : values()) if (part.id().equals(normalized)) return DataResult.success(part);
        return DataResult.error(() -> "Unknown material part: " + name);
    }, MaterialPart::id);
}
