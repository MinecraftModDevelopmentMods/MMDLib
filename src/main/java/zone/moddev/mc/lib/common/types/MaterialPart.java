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
    ORE(Kind.BLOCK), ENDORE(Kind.BLOCK), NETHERORE(Kind.BLOCK);

    /** Legacy source-level name and JSON alias; it must not generate a second crystal. */
    public static final MaterialPart MEK_CRYSTAL = CRYSTAL;

    public enum Kind { ITEM, BLOCK, ITEM_BLOCK }
    public enum Handling {
        BASIC_ITEM, BASIC_BLOCK, TOOL, ARMOR, ANIMAL_ARMOR, AMMUNITION, BOW, CROSSBOW,
        FISHING_ROD, SHEARS, SHIELD, CONNECTING_BLOCK, BOOKSHELF, BUTTON, SLAB,
        FLOWER_POT, LADDER, LEVER, PLATE, PRESSURE_PLATE, STAIRS, TRAPDOOR,
        TRIPWIRE_HOOK, FENCE_GATE, ANVIL, DOOR, ORE
    }

    /** Runtime factories must implement this behavior, not substitute a generic Item or Block. */
    public Handling handling() {
        return switch (this) {
            case HELMET, CHESTPLATE, LEGGINGS, BOOTS -> Handling.ARMOR;
            case HORSE_ARMOR -> Handling.ANIMAL_ARMOR;
            case ARROW, BOLT -> Handling.AMMUNITION;
            case AXE, HOE, PICKAXE, SHOVEL, SWORD, CRACKHAMMER, SCYTHE -> Handling.TOOL;
            case BOW -> Handling.BOW;
            case CROSSBOW -> Handling.CROSSBOW;
            case FISHING_ROD -> Handling.FISHING_ROD;
            case SHEARS -> Handling.SHEARS;
            case SHIELD -> Handling.SHIELD;
            case BARS, WALL, FENCE -> Handling.CONNECTING_BLOCK;
            case BOOKSHELF -> Handling.BOOKSHELF;
            case BUTTON -> Handling.BUTTON;
            case SLAB, DOUBLE_SLAB -> Handling.SLAB;
            case FLOWER_POT -> Handling.FLOWER_POT;
            case LADDER -> Handling.LADDER;
            case LEVER -> Handling.LEVER;
            case PLATE -> Handling.PLATE;
            case PRESSURE_PLATE -> Handling.PRESSURE_PLATE;
            case STAIRS -> Handling.STAIRS;
            case TRAPDOOR -> Handling.TRAPDOOR;
            case TRIPWIRE_HOOK -> Handling.TRIPWIRE_HOOK;
            case FENCE_GATE -> Handling.FENCE_GATE;
            case ANVIL -> Handling.ANVIL;
            case DOOR -> Handling.DOOR;
            case ORE, ENDORE, NETHERORE -> Handling.ORE;
            default -> kind == Kind.ITEM ? Handling.BASIC_ITEM : Handling.BASIC_BLOCK;
        };
    }
    public boolean requiresSpecializedFactory() {
        return handling() != Handling.BASIC_ITEM && handling() != Handling.BASIC_BLOCK;
    }
    public Optional<net.minecraft.world.entity.EquipmentSlot> armorSlot() {
        return switch (this) {
            case HELMET -> Optional.of(net.minecraft.world.entity.EquipmentSlot.HEAD);
            case CHESTPLATE -> Optional.of(net.minecraft.world.entity.EquipmentSlot.CHEST);
            case LEGGINGS -> Optional.of(net.minecraft.world.entity.EquipmentSlot.LEGS);
            case BOOTS -> Optional.of(net.minecraft.world.entity.EquipmentSlot.FEET);
            default -> Optional.empty();
        };
    }
    public boolean hasPlacementLogic() {
        return switch (handling()) {
            case CONNECTING_BLOCK, BUTTON, SLAB, FLOWER_POT, LADDER, LEVER, PLATE,
                 PRESSURE_PLATE, STAIRS, TRAPDOOR, TRIPWIRE_HOOK, FENCE_GATE, ANVIL, DOOR -> true;
            default -> false;
        };
    }
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
    public static DataResult<MaterialPart> fromName(String name) {
        String normalized = switch (name) {
            case "powder" -> "dust";
            case "mek_crystal" -> "crystal";
            case "end_ore" -> "endore";
            case "nether_ore" -> "netherore";
            default -> name;
        };
        for (MaterialPart part : values()) if (part.id().equals(normalized)) return DataResult.success(part);
        return DataResult.error(() -> "Unknown material part: " + name);
    }
    public static final Codec<MaterialPart> CODEC = Codec.STRING.comapFlatMap(MaterialPart::fromName, MaterialPart::id);
}
