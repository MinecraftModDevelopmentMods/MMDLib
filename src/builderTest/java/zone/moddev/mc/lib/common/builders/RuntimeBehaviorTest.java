package zone.moddev.mc.lib.common.builders;

import zone.moddev.mc.lib.common.modules.ModuleCatalog;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.*;
import zone.moddev.mc.lib.common.runtime.*;
import zone.moddev.mc.lib.common.types.MaterialPart;
import zone.moddev.mc.lib.api.MaterialExtensions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class RuntimeBehaviorTest {
    // Built-in factories are found through the public registry, exactly as an addon's would be.
    private static final MaterialExtensions API = new MaterialExtensions();
    private static Class<?> itemClass(MaterialPart part) { return API.itemImplementation(part.factory()).itemClass(); }
    private static Class<?> blockClass(MaterialPart part) { return API.blockImplementation(part.factory()).blockClass(); }
    private static void blockValidate(MaterialPart part) { API.blockImplementation(part.factory()).validate(part); }
    public static void main(String[] args) throws Exception {
        var material = MaterialBuilder.create().setName("test:metal").setHardness(8).setStrength(8).setMagic(4.5).setDamage(2).build();
        var stats = new MaterialStats(material);
        require(stats.toolDurability() == 256 && stats.scaledDurability("crackhammer_durability", .75) == 192);
        require(stats.toolEfficiency() == 8 && stats.scaledEfficiency("crackhammer_destroy_speed", .5, 1) == 4);
        require(stats.enchantability() == 11 && stats.armorDurabilityFactor() == 16);
        require(stats.armorProtection(EquipmentSlot.HEAD) == 2 && stats.armorProtection(EquipmentSlot.CHEST) == 6);
        require(stats.armorProtection(EquipmentSlot.LEGS) == 5 && stats.armorProtection(EquipmentSlot.FEET) == 2);
        // Bridge resolution: an item definition's binding wins; otherwise its material's binding applies.
        var bound = MaterialBuilder.from(material).setEventMap(Map.of("player-tick", ResourceLocation.parse("test:material_tick"),
                "projectile-hit", ResourceLocation.parse("test:material_hit"))).build();
        var arrow = ItemBuilder.create().setName("test:arrow").setEventMap(Map.of("projectile-hit", ResourceLocation.parse("test:item_hit")))
                .buildFor(ResourceLocation.parse("test:metal"));
        require(ItemEventBridge.binding(arrow.events(), bound, "projectile-hit").toString().equals("test:item_hit"));
        require(ItemEventBridge.binding(arrow.events(), bound, "player-tick").toString().equals("test:material_tick"));
        require(ItemEventBridge.binding(arrow.events(), bound, "projectile-damage") == null && ItemEventBridge.binding(arrow.events(), null, "player-tick") == null);
        var tuned = MaterialBuilder.from(material).setSpecial(Map.of("tool_durability", 500D, "crackhammer_destroy_speed", 7D)).build();
        require(new MaterialStats(tuned).toolDurability() == 500 && new MaterialStats(tuned).scaledEfficiency("crackhammer_destroy_speed", .5, 1) == 7);
        AtomicInteger repairs = new AtomicInteger();
        var incorrect = TagKey.<Block>create(Registries.BLOCK, ResourceLocation.parse("test:incorrect_for_metal"));
        var tier = new MaterialToolTier(material, incorrect, () -> { repairs.incrementAndGet(); throw new AssertionError("Early repair resolution"); });
        require(tier.getIncorrectBlocksForDrops().equals(incorrect));
        require(tier.getUses() == 256 && tier.getAttackDamageBonus() == 2 && tier.getEnchantmentValue() == 11);
        require(repairs.get() == 0);
        require(itemClass(ModuleCatalog.bundled().parts().resolve("scythe").getOrThrow()) == ScytheItem.class);
        require(itemClass(ModuleCatalog.bundled().parts().resolve("crackhammer").getOrThrow()) == StandardItems.Tool.class);
        var hammer = StandardItems.ToolSettings.of(ModuleCatalog.bundled().parts().resolve("crackhammer").getOrThrow());
        require(hammer.fastOn().orElseThrow().location().toString().equals("mmdlib:crackhammer_crushable")
                && hammer.durability().toolFactor() == .75 && hammer.speed().minimum() == 1 && !hammer.abilities().isEmpty());
        for (var part : java.util.Set.of(ModuleCatalog.bundled().parts().resolve("helmet").getOrThrow(), ModuleCatalog.bundled().parts().resolve("chestplate").getOrThrow(), ModuleCatalog.bundled().parts().resolve("leggings").getOrThrow(), ModuleCatalog.bundled().parts().resolve("boots").getOrThrow()))
            require(ArmorItem.class.isAssignableFrom(itemClass(part)));
        // Pressure plates: no `detects` keeps the vanilla plate; `detects` selects the filtering plate; bad values fail at load.
        var plate = ModuleCatalog.bundled().parts().resolve("pressure_plate").getOrThrow();
        require(blockClass(plate) == net.minecraft.world.level.block.PressurePlateBlock.class);
        java.util.function.Function<String, zone.moddev.mc.lib.common.types.MaterialPart> detecting = value -> new zone.moddev.mc.lib.common.types.MaterialPart(
                plate.templateId(), plate.kind(), plate.requiredMod(), plate.metadata(), plate.factory(),
                new zone.moddev.mc.lib.common.types.FactoryData(com.google.gson.JsonParser.parseString("{\"detects\":\"" + value + "\"}")));
        for (String value : java.util.List.of("everything", "mobs", "players", "#example:detectable")) {
            blockValidate(detecting.apply(value));
            require(blockClass(detecting.apply(value)) == net.minecraft.world.level.block.PressurePlateBlock.class);
        }
        try { blockValidate(detecting.apply("ghosts")); require(false); } catch (IllegalArgumentException expected) {}
        // Redstone settings on the generic block factory: decoded at load, malformed settings rejected.
        var plain = ModuleCatalog.bundled().parts().resolve("block").getOrThrow();
        java.util.function.Function<String, MaterialPart> redstone = settings -> new MaterialPart(plain.templateId(), plain.kind(),
                plain.requiredMod(), plain.metadata(), plain.factory(),
                new zone.moddev.mc.lib.common.types.FactoryData(JsonParser.parseString("{\"redstone\":" + settings + "}")));
        require(RedstoneBlock.Settings.of(plain).isEmpty());
        var lamp = RedstoneBlock.Settings.of(redstone.apply("{\"input\":true,\"comparator\":\"input\"}")).orElseThrow();
        require(lamp.input() && !lamp.emits() && lamp.comparatorProperty().orElseThrow() == RedstoneBlock.INPUT);
        var source = RedstoneBlock.Settings.of(redstone.apply("{\"output\":15,\"strong\":true,\"faces\":[\"down\"]}")).orElseThrow();
        require(source.emits() && source.strong() && source.constantOutput().orElseThrow() == 15
                && source.faces().equals(java.util.Set.of(net.minecraft.core.Direction.DOWN)));
        require(RedstoneBlock.Settings.of(redstone.apply("{\"output\":\"state\",\"comparator\":\"output\"}")).orElseThrow().stateOutput());
        for (String bad : java.util.List.of("{\"output\":16}", "{\"output\":\"loud\"}", "{\"faces\":[\"sideways\"]}", "{\"comparator\":\"input\"}",
                "{\"comparator\":\"output\",\"output\":7}", "{\"inputs\":true}", "{\"output\":15,\"faces\":[]}", "true"))
            try { blockValidate(redstone.apply(bad)); require(false); } catch (IllegalArgumentException expected) {}
        var redstoneHandler = ResourceLocation.parse("test:redstone_changed");
        API.events().registerCallback(redstoneHandler, zone.moddev.mc.lib.api.MaterialEventTypes.REDSTONE_CHANGED, change -> {});
        API.events().validate(Map.of("redstone-changed", redstoneHandler));
        // Weighted plates require a weight.
        var weighted = ModuleCatalog.bundled().parts().resolve("weighted_pressure_plate").getOrThrow();
        blockValidate(weighted);
        require(blockClass(weighted) == net.minecraft.world.level.block.WeightedPressurePlateBlock.class);
        try { blockValidate(new MaterialPart(weighted.templateId(), weighted.kind(), weighted.requiredMod(), weighted.metadata(), weighted.factory(),
                new zone.moddev.mc.lib.common.types.FactoryData(JsonParser.parseString("{\"max_weight\":0}")))); require(false); }
        catch (IllegalArgumentException expected) {}
        require(blockClass(ModuleCatalog.bundled().parts().resolve("stairs").getOrThrow()) == StairBlock.class);
        require(blockClass(ModuleCatalog.bundled().parts().resolve("slab").getOrThrow()) == SlabBlock.class);
        require(blockClass(ModuleCatalog.bundled().parts().resolve("plate").getOrThrow()) == PlateBlock.class);
        require(blockClass(ModuleCatalog.bundled().parts().resolve("ladder").getOrThrow()) == LadderBlock.class);
        require(blockClass(ModuleCatalog.bundled().parts().resolve("lever").getOrThrow()) == LeverBlock.class);
        for (MaterialPart part : ModuleCatalog.bundled().parts().entries().values()) {
            if (part.kind() != MaterialPart.Kind.ITEM) require(blockClass(part) != null);
            else require(itemClass(part) != null);
        }
        require(itemClass(ModuleCatalog.bundled().parts().resolve("arrow").getOrThrow()) == MaterialAmmoItem.class);
        expectFailure(() -> blockClass(ModuleCatalog.bundled().parts().resolve("sword").getOrThrow()));
        expectFailure(() -> new MaterialStats(MaterialBuilder.from(material).setSpecial(Map.of("tool_durability", 0D)).build()).toolDurability());
        Path resources = Path.of(System.getProperty("mmdlib.resources"));
        for (String name : java.util.List.of("scythe_harvestable", "crackhammer_crushable")) {
            var data = JsonParser.parseString(Files.readString(resources.resolve("data/mmdlib/tags/block/" + name + ".json"))).getAsJsonObject();
            require(!data.getAsJsonArray("values").isEmpty());
            for (var value : data.getAsJsonArray("values")) {
                if (value.isJsonObject()) require(!value.getAsJsonObject().get("required").getAsBoolean());
                else require(value.getAsString().startsWith("minecraft:"));
            }
        }
        System.out.println("BMe-derived runtime class dispatch, material stat formulas, explicit tier tags, and tag resources passed");
    }
    private static void require(boolean condition) {
        if (!condition) throw new AssertionError("Runtime support assertion failed");
    }
    private static void expectFailure(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("Expected invalid runtime type/stat to fail");
    }
}
