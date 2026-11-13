package zone.moddev.mc.lib.common.runtime;

import java.nio.charset.StandardCharsets;

import java.util.Collection;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import zone.moddev.mc.lib.common.factories.MaterialFactory;

/**
 * The tags every material item gets from its part: tool, armor and
 * enchantability tags, {@code minecraft:arrows} and {@code c:arrows}, and
 * {@code c:<form>/<material>} for ingots, dusts, nuggets, rods and gears.
 * MMDLib adds them for every loaded material, whatever its source, through
 * {@link MaterialTagPack}.
 */
public final class MaterialItemTags {
    private static final Set<String> ARMOR = Set.of("helmet", "chestplate",
        "leggings", "boots");
    private static final Set<String> DURABLE = Set.of("bow", "crossbow",
        "fishing_rod", "shears", "shield", "axe", "pickaxe", "shovel", "hoe",
        "sword", "crackhammer", "scythe", "helmet", "chestplate", "leggings",
        "boots");
    private static final Set<String> MINING = Set.of("axe", "pickaxe", "shovel",
        "hoe", "crackhammer", "scythe");
    private static final Set<String> WEAPON = Set.of("sword", "axe", "scythe",
        "crackhammer");
    private static final Set<String> VANILLA_TOOL = Set.of("pickaxe", "axe",
        "shovel", "hoe", "sword");

    private MaterialItemTags() {
    }

    /**
     * Passes each tag an item of {@code part} belongs to, with the entry to add
     * (the item ID, or a {@code #tag} for the parent common tags).
     *
     * @param part the part's short ID, such as {@code sword} ({@code mmdlib}
     *            parts) or a full ID for other mods' parts, which get no tags
     * @param material the material's name, the path of its ID
     * @param item the item's registry ID
     * @param tag receives the tag ID and the entry
     */
    public static void forPart(String part, String material, String item,
        BiConsumer<String, String> tag) {
        switch (part) {
            case "bow" -> tag.accept("minecraft:enchantable/bow", item);
            case "crossbow" ->
                tag.accept("minecraft:enchantable/crossbow", item);
            case "fishing_rod" ->
                tag.accept("minecraft:enchantable/fishing", item);
            default -> {
            }
        }
        if (DURABLE.contains(part))
            tag.accept("minecraft:enchantable/durability", item);
        if (MINING.contains(part)) {
            tag.accept("minecraft:enchantable/mining", item);
            tag.accept("minecraft:enchantable/mining_loot", item);
        }
        if (WEAPON.contains(part)) {
            tag.accept("minecraft:enchantable/weapon", item);
            tag.accept("minecraft:enchantable/sharp_weapon", item);
        }
        if (part.equals("sword"))
            tag.accept("minecraft:enchantable/sword", item);
        if (part.equals("arrow") || part.equals("bolt")) {
            tag.accept("minecraft:arrows", item);
            tag.accept("c:arrows", item);
        }
        if (VANILLA_TOOL.contains(part))
            tag.accept("minecraft:" + part + "s", item);
        if (ARMOR.contains(part)) {
            tag.accept("minecraft:enchantable/armor", item);
            tag.accept("minecraft:enchantable/" + switch (part) {
                case "helmet" -> "head_armor";
                case "chestplate" -> "chest_armor";
                case "leggings" -> "leg_armor";
                default -> "foot_armor";
            }, item);
        }
        var common = switch (part) {
            case "dust" -> "dusts";
            case "ingot" -> "ingots";
            case "nugget" -> "nuggets";
            case "rod" -> "rods";
            case "gear" -> "gears";
            default -> null;
        };
        if (common != null) {
            tag.accept("c:" + common + "/" + material, item);
            tag.accept("c:" + common, "#c:" + common + "/" + material);
        }
    }

    /**
     * The item tag files for {@code materials}, by pack path, such as
     * {@code data/minecraft/tags/item/swords.json}. Each adds to its tag
     * ({@code replace} is false) and lists each entry once, as optional, so an
     * item a mod loaded but did not register cannot break the tag. Parts that
     * are blocks are left to their block's item.
     */
    public static SortedMap<String, byte[]> data(
        Collection<MaterialFactory.Result> materials) {
        var tags = new TreeMap<String, TreeSet<String>>();
        for (var result : materials) {
            var name = result.material().name().orElseThrow().getPath();
            var plan = result.content();
            plan.items().forEach((part, item) -> {
                if (!plan.blocks().containsKey(part))
                    forPart(part.id(), name, item.name().toString(),
                        (tag, entry) -> tags
                            .computeIfAbsent(tag, key -> new TreeSet<>())
                            .add(entry));
            });
        }
        var gson = new GsonBuilder().setPrettyPrinting().create();
        var files = new TreeMap<String, byte[]>();
        tags.forEach((tag, entries) -> {
            var separator = tag.indexOf(':');
            var body = new JsonObject();
            body.addProperty("replace", false);
            var values = new JsonArray();
            for (var entry : entries) {
                var value = new JsonObject();
                value.addProperty("id", entry);
                value.addProperty("required", false);
                values.add(value);
            }
            body.add("values", values);
            files.put(
                "data/" + tag.substring(0, separator) + "/tags/item/"
                    + tag.substring(separator + 1) + ".json",
                gson.toJson(body).getBytes(StandardCharsets.UTF_8));
        });
        return files;
    }
}
