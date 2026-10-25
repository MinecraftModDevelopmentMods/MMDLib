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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class RuntimeBehaviorTest {
    public static void main(String[] args) throws Exception {
        var material = MaterialBuilder.create().setName("test:metal").setHardness(8).setStrength(8).setMagic(4.5).setDamage(2).build();
        var stats = new MaterialStats(material);
        require(stats.toolDurability() == 256 && stats.crackhammerDurability() == 192);
        require(stats.toolEfficiency() == 8 && stats.crackhammerDestroySpeed() == 4);
        require(stats.enchantability() == 11 && stats.armorDurabilityFactor() == 16);
        require(stats.armorProtection(EquipmentSlot.HEAD) == 2 && stats.armorProtection(EquipmentSlot.CHEST) == 6);
        require(stats.armorProtection(EquipmentSlot.LEGS) == 5 && stats.armorProtection(EquipmentSlot.FEET) == 2);
        var tuned = MaterialBuilder.from(material).setSpecial(Map.of("tool_durability", 500D, "crackhammer_destroy_speed", 7D)).build();
        require(new MaterialStats(tuned).toolDurability() == 500 && new MaterialStats(tuned).crackhammerDestroySpeed() == 7);
        AtomicInteger repairs = new AtomicInteger();
        var incorrect = TagKey.<Block>create(Registries.BLOCK, ResourceLocation.parse("test:incorrect_for_metal"));
        var tier = new MaterialToolTier(material, incorrect, () -> { repairs.incrementAndGet(); throw new AssertionError("Early repair resolution"); });
        require(tier.getIncorrectBlocksForDrops().equals(incorrect));
        require(tier.getUses() == 256 && tier.getAttackDamageBonus() == 2 && tier.getEnchantmentValue() == 11);
        require(repairs.get() == 0);
        require(StandardItems.itemClass(ModuleCatalog.bundled().parts().resolve("scythe").getOrThrow()) == ScytheItem.class);
        require(StandardItems.itemClass(ModuleCatalog.bundled().parts().resolve("crackhammer").getOrThrow()) == CrackhammerItem.class);
        for (var part : java.util.Set.of(ModuleCatalog.bundled().parts().resolve("helmet").getOrThrow(), ModuleCatalog.bundled().parts().resolve("chestplate").getOrThrow(), ModuleCatalog.bundled().parts().resolve("leggings").getOrThrow(), ModuleCatalog.bundled().parts().resolve("boots").getOrThrow()))
            require(ArmorItem.class.isAssignableFrom(StandardItems.itemClass(part)));
        require(StandardBlocks.blockClass(ModuleCatalog.bundled().parts().resolve("stairs").getOrThrow()) == StairBlock.class);
        require(StandardBlocks.blockClass(ModuleCatalog.bundled().parts().resolve("slab").getOrThrow()) == SlabBlock.class);
        require(StandardBlocks.blockClass(ModuleCatalog.bundled().parts().resolve("plate").getOrThrow()) == PlateBlock.class);
        require(StandardBlocks.blockClass(ModuleCatalog.bundled().parts().resolve("ladder").getOrThrow()) == LadderBlock.class);
        require(StandardBlocks.blockClass(ModuleCatalog.bundled().parts().resolve("lever").getOrThrow()) == LeverBlock.class);
        for (MaterialPart part : ModuleCatalog.bundled().parts().entries().values()) {
            if (part.kind() != MaterialPart.Kind.ITEM) require(StandardBlocks.blockClass(part) != null);
            else if (!part.needsEntity()) require(StandardItems.itemClass(part) != null);
        }
        expectFailure(() -> StandardItems.itemClass(ModuleCatalog.bundled().parts().resolve("arrow").getOrThrow()));
        expectFailure(() -> StandardBlocks.blockClass(ModuleCatalog.bundled().parts().resolve("sword").getOrThrow()));
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
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected invalid runtime type/stat to fail");
    }
}
