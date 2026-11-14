package zone.moddev.mc.companion.test;

import java.util.*;

import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;

import zone.moddev.mc.lib.api.automation.ResourceType;
import zone.moddev.mc.lib.gui.Screens;
import zone.moddev.mc.lib.machine.neoforge.*;
import zone.moddev.mc.lib.machine.recipe.*;
import zone.moddev.mc.lib.machine.recipe.generation.*;

@GameTestHolder("mmdlib_companion")
@PrefixGameTestTemplate(false)
public final class SimpleCrusherTests {
    private static final ResourceLocation ID = ResourceLocation
        .parse("mmdlib_companion:simple_crusher");
    private static final ResourceLocation TYPE = ResourceLocation
        .parse("mmdlib_companion:simple_crushing");
    private static final ResourceLocation IRON = ResourceLocation
        .parse("mmdlib_companion:generated/simple_crushing/ores/iron");

    private static DefinedMachineEntity place(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        h.getLevel().setBlock(pos,
            BuiltInRegistries.BLOCK.get(ID).defaultBlockState(), 3);
        return (DefinedMachineEntity) h.getLevel().getBlockEntity(pos);
    }

    private static void require(boolean value, String message) {
        if (!value)
            throw new AssertionError(message);
    }

    @GameTest(template = "empty")
    public static void generatedRecipesDriveAClasslessCrusher(
        GameTestHelper h) {
        var machine = place(h);
        var type = MachineContent.recipeType(TYPE);
        require(machine.getClass() == DefinedMachineEntity.class,
            "Generic entity, no crusher subclass");
        var holder = type.recipes(h.getLevel().getRecipeManager()).stream()
            .filter(r -> r.id()
                .equals(ResourceLocation.parse(IRON + "/minecraft/iron_ore")))
            .findFirst().orElseThrow();
        require(holder.value().recipe().time() == 130,
            "XML hardness scaling applied to iron ore");
        machine.energy.setStored(10000);
        require(machine.items
            .insertItem(0, new ItemStack(Items.IRON_ORE), false).isEmpty(),
            "Recipe-linked ore admission");
        for (int i = 0; i < 131; i++)
            machine.serverTick();
        require(machine.items.getStackInSlot(0).isEmpty(),
            "Consumed input group");
        require(
            machine.items.getStackInSlot(1).is(Items.IRON_INGOT)
                && machine.items.getStackInSlot(1).getCount() == 2,
            "Default output count and XML group");
        require(machine.energy.stored() == 7400,
            "XML hardness scaling applied to total energy");
        require(Screens.get(ID).orElseThrow().screen()
            .find("generated_progress").isPresent(),
            "Generated screen installed");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void capabilitiesSaveAndReservationsUseInheritedStorage(
        GameTestHelper h) {
        var machine = place(h);
        var pos = machine.getBlockPos();
        var items = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
            pos, Direction.NORTH);
        var power = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,
            pos, Direction.NORTH);
        require(items != null && items.getSlots() == 3 && power != null,
            "Capabilities registered automatically");
        require(items.insertItem(1, new ItemStack(Items.IRON_ORE), false)
            .getCount() == 1, "Outputs refuse automation insertion");
        require(items.insertItem(0, new ItemStack(Items.DIRT), false)
            .getCount() == 1, "Recipe admission refuses unrelated items");
        power.receiveEnergy(1000, false);
        power.receiveEnergy(1000, false);
        power.receiveEnergy(600, false);
        items.insertItem(0, new ItemStack(Items.IRON_ORE), false);
        for (int i = 0; i < 26; i++)
            machine.serverTick();
        var saved = machine.saveWithoutMetadata(h.getLevel().registryAccess());
        machine.loadWithComponents(saved, h.getLevel().registryAccess());
        require(machine.getProgress() == 25,
            "Reserved cycle restored after storage");
        for (int i = 0; i < 105; i++)
            machine.serverTick();
        require(
            machine.items.getStackInSlot(1).getCount() == 2
                && machine.energy.stored() == 0,
            "Resumed without duplicating inputs or power");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void blockedOutputsDoNotConsumeResources(GameTestHelper h) {
        var machine = place(h);
        machine.energy.setStored(10000);
        machine.items.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 64));
        machine.items.setStackInSlot(2, new ItemStack(Items.GOLD_INGOT, 64));
        machine.items.insertItem(0, new ItemStack(Items.IRON_ORE), false);
        machine.serverTick();
        require(
            machine.items.getStackInSlot(0).getCount() == 1
                && machine.energy.stored() == 10000,
            "Blocked output holds resources");
        machine.items.setStackInSlot(2, ItemStack.EMPTY);
        for (int i = 0; i < 131; i++)
            machine.serverTick();
        require(machine.items.getStackInSlot(2).is(Items.IRON_INGOT),
            "Products choose available member of output group");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void unknownMetalsAndAuthoredOverridesAndFreshGeneration(
        GameTestHelper h) {
        var tags = new TreeMap<ResourceLocation, List<Holder<Item>>>();
        tags.put(ResourceLocation.parse("c:ores/testium"),
            List.of(Items.ANCIENT_DEBRIS.builtInRegistryHolder()));
        tags.put(ResourceLocation.parse("c:ingots/testium"),
            List.of(Items.NETHERITE_INGOT.builtInRegistryHolder(),
                Items.IRON_INGOT.builtInRegistryHolder()));
        var id = ResourceLocation.parse(
            "mmdlib_companion:generated/simple_crushing/ores/testium/minecraft/ancient_debris");
        var generated = GeneratedMachineRecipes.generate(List.of(),
            h.getLevel().registryAccess(), new ReloadTags(tags));
        var recipe = (MachineRecipeEntry) generated.stream()
            .filter(r -> r.id().equals(id)).findFirst().orElseThrow().value();
        require(recipe.getResultItem(h.getLevel().registryAccess())
            .is(Items.IRON_INGOT), "Deterministic output tie-break");
        var again = GeneratedMachineRecipes.generate(List.of(),
            h.getLevel().registryAccess(), new ReloadTags(tags));
        require(again.size() == generated.size(),
            "Regeneration does not accumulate entries");
        var authored = new RecipeHolder<>(id,
            new MachineRecipeEntry(MachineContent.recipeType(TYPE),
                MachineRecipeBuilder.recipe(MachineContent.recipeType(TYPE))
                    .inputItems("input",
                        net.minecraft.world.item.crafting.Ingredient
                            .of(Items.ANCIENT_DEBRIS),
                        1)
                    .outputItem("output", new ItemStack(Items.DIAMOND)).time(1)
                    .build()));
        var combined = GeneratedMachineRecipes.generate(List.of(authored),
            h.getLevel().registryAccess(), new ReloadTags(tags));
        require(combined.stream().filter(r -> r.id().equals(id)).findFirst()
            .orElseThrow() == authored, "Authored ID overrides generated");
        require(GeneratedMachineRecipes.generate(List.of(),
            h.getLevel().registryAccess(), new ReloadTags(Map.of())).isEmpty(),
            "Removed tags remove recipes");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void generatorDefaultsAndOrderedPreferences(
        GameTestHelper h) {
        // Any stackable item from a mod, such as the companion's own.
        var modItem = BuiltInRegistries.ITEM.stream()
            .filter(item -> !BuiltInRegistries.ITEM.getKey(item).getNamespace()
                .equals("minecraft")
                && item.getDefaultInstance().getMaxStackSize() >= 2)
            .findFirst().orElseThrow();
        var modNamespace = BuiltInRegistries.ITEM.getKey(modItem)
            .getNamespace();
        var tags = new ReloadTags(
            Map.of(ResourceLocation.parse("c:ores/testium"),
                List.of(Items.IRON_ORE.builtInRegistryHolder()),
                ResourceLocation.parse("c:ingots/testium"),
                List.of(modItem.builtInRegistryHolder(),
                    Items.IRON_INGOT.builtInRegistryHolder())));
        var defaults = direct(h, tags, Map.of()).values().iterator().next();
        require(
            defaults.time() == 100
                && defaults.inputs().get("energy").getFirst().amount() == 2000,
            "Code defaults");
        require(
            defaults.inputs().get("input").getFirst().amount() == 1
                && product(defaults).getCount() == 2,
            "One ore to two ingots independent of slots");
        require(product(defaults).is(Items.IRON_INGOT),
            "Minecraft preferred by default");
        var renamed = direct(h, tags, Map.of("input-group", "ores",
            "output-group", "ingots", "energy-group", "power")).values()
            .iterator().next();
        require(
            renamed.inputs().keySet().equals(Set.of("ores", "power"))
                && renamed.outputs().keySet().equals(Set.of("ingots")),
            "Optional group name overrides");
        var custom = direct(h, tags,
            Map.of("preferred-mods", "missing; " + modNamespace + " ;minecraft",
                "time", "17", "energy", "41"))
            .values().iterator().next();
        require(product(custom).is(modItem),
            "First available preferred namespace wins");
        require(
            custom.time() == 17
                && custom.inputs().get("energy").getFirst().amount() == 41,
            "Optional cost overrides");
        var reversed = direct(h, tags,
            Map.of("preferred-mods", "minecraft;mmdlib")).values().iterator()
            .next();
        require(product(reversed).is(Items.IRON_INGOT),
            "Preference order changes output");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void generatorScalesEachOreUsingStagedBlockTags(
        GameTestHelper h) {
        Map<ResourceLocation, List<Holder<Item>>> items = Map.of(
            ResourceLocation.parse("c:ores/iron"),
            List.of(Items.IRON_ORE.builtInRegistryHolder(),
                Items.DEEPSLATE_IRON_ORE.builtInRegistryHolder()),
            ResourceLocation.parse("c:ingots/iron"),
            List.of(Items.IRON_INGOT.builtInRegistryHolder()));
        List<Holder<net.minecraft.world.level.block.Block>> ores = List.of(
            Blocks.IRON_ORE.builtInRegistryHolder(),
            Blocks.DEEPSLATE_IRON_ORE.builtInRegistryHolder());
        var tags = new ReloadTags(items,
            Map.of(BlockTags.NEEDS_IRON_TOOL.location(), ores));
        var props = Map.of("tool-level-ratio", "0.25", "hardness-level-ratio",
            "0.1");
        var generated = direct(h, tags, props);
        var iron = generated
            .get(ResourceLocation.parse(IRON + "/minecraft/iron_ore"));
        var deep = generated.get(
            ResourceLocation.parse(IRON + "/minecraft/deepslate_iron_ore"));
        require(
            generated.size() == 2 && iron.time() == 180 && deep.time() == 195,
            "Per-block hardness and staged tool requirement");
        require(
            iron.inputs().get("energy").getFirst().amount() == 3600
                && deep.inputs().get("energy").getFirst().amount() == 3900,
            "Scale total energy with same multiplier");
        var ingredient = (zone.moddev.mc.lib.machine.ingredient.MachineIngredient.ItemPart) iron
            .inputs().get("input").getFirst();
        require(
            ingredient.items().test(new ItemStack(Items.IRON_ORE))
                && !ingredient.items()
                    .test(new ItemStack(Items.DEEPSLATE_IRON_ORE)),
            "Each cost matches only its ore variant");
        var changed = direct(h, new ReloadTags(items,
            Map.of(BlockTags.NEEDS_DIAMOND_TOOL.location(), ores)), props);
        require(
            changed.get(ResourceLocation.parse(IRON + "/minecraft/iron_ore"))
                .time() == 205,
            "New staged tags replace old requirements");
        var rounded = direct(h, tags,
            Map.of("time", "1", "energy", "1", "hardness-level-ratio", "0.01"));
        require(
            rounded.values().stream()
                .allMatch(recipe -> recipe.time() == 2
                    && recipe.inputs().get("energy").getFirst().amount() == 2),
            "Fractional costs round up");
        var perLevel = direct(h, tags, Map.of("tool-level-ratio", ".10"));
        require(
            perLevel.values().stream().allMatch(recipe -> recipe.time() == 120
                && recipe.inputs().get("energy").getFirst().amount() == 2400),
            ".10 adds ten percent per level without compounding");
        var stoneTags = new ReloadTags(items,
            Map.of(BlockTags.NEEDS_STONE_TOOL.location(), ores));
        var boundary = direct(h, stoneTags,
            Map.of("tool-level-ratio", ".10", "energy", "100"));
        require(
            boundary.values().stream().allMatch(recipe -> recipe.time() == 110
                && recipe.inputs().get("energy").getFirst().amount() == 110),
            "Double roundoff at 100 * 1.1 does not add an extra unit");
        var fraction = direct(h, stoneTags,
            Map.of("tool-level-ratio", ".100001", "energy", "100"));
        require(
            fraction.values().stream().allMatch(recipe -> recipe.time() == 111
                && recipe.inputs().get("energy").getFirst().amount() == 111),
            "Real fractional costs still round up");
        var zero = direct(h, tags,
            Map.of("tool-level-ratio", "0.0", "hardness-level-ratio", "0.0"));
        require(
            zero.size() == 1 && zero.get(IRON).time() == 100
                && zero.get(IRON).inputs().get("energy").getFirst()
                    .amount() == 2000,
            "Explicit zero ratios preserve default costs and tag recipe IDs");
        var unchanged = direct(h, new ReloadTags(items),
            Map.of("tool-level-ratio", ".10", "time",
                Integer.toString(Integer.MAX_VALUE), "energy",
                Long.toString(Long.MAX_VALUE)));
        require(unchanged.values().stream()
            .allMatch(recipe -> recipe.time() == Integer.MAX_VALUE && recipe
                .inputs().get("energy").getFirst().amount() == Long.MAX_VALUE),
            "Factor one preserves exact maximum integer costs");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void generatorRejectsBadOptionsAndOverflow(GameTestHelper h) {
        for (var option : List.of(Map.of("preferred-mods", "minecraft:mmdlib"),
            Map.of("preferred-mods", "minecraft;"),
            Map.of("tool-level-ratio", "NaN"),
            Map.of("hardness-level-ratio", "Infinity"),
            Map.of("hardness-level-ratio", "-0.1"),
            Map.of("preferred-mod", "minecraft"), Map.of("input-count", "2"),
            Map.of("time", "0"))) {
            try {
                direct(h, new ReloadTags(Map.of()), option);
                throw new AssertionError("Accepted invalid options " + option);
            } catch (IllegalArgumentException expected) {
                require(expected.getMessage().contains("test-generator.xml:1"),
                    "Source-aware option error");
            }
        }
        var tags = new ReloadTags(Map.of(ResourceLocation.parse("c:ores/iron"),
            List.of(Items.IRON_ORE.builtInRegistryHolder()),
            ResourceLocation.parse("c:ingots/iron"),
            List.of(Items.IRON_INGOT.builtInRegistryHolder())));
        try {
            direct(h, tags, Map.of("energy", Long.toString(Long.MAX_VALUE),
                "hardness-level-ratio", "1"));
            throw new AssertionError("Accepted energy overflow");
        } catch (IllegalArgumentException expected) {
            require(expected.getMessage().contains("Scaled energy"),
                "Overflow fails instead of wrapping");
        }
        for (var options : List.of(
            Map.of("time", Integer.toString(Integer.MAX_VALUE),
                "hardness-level-ratio", ".10"),
            Map.of("hardness-level-ratio", "1e308"))) {
            try {
                direct(h, tags, options);
                throw new AssertionError(
                    "Accepted non-finite or overflowing scaled time");
            } catch (IllegalArgumentException expected) {
                require(expected.getMessage().contains("Scaled time"),
                    "Reject time overflow and non-finite products");
            }
        }
        h.succeed();
    }

    private static Map<ResourceLocation, MachineRecipe> direct(GameTestHelper h,
        ReloadTags tags, Map<String, String> options) {
        var result = new TreeMap<ResourceLocation, MachineRecipe>();
        new OreToIngotGenerator().generate(
            new RecipeGenerationContext(MachineContent.recipeType(TYPE),
                h.getLevel().registryAccess(), tags, List.of(),
                new GeneratorProperties(options, "test-generator.xml", 1)),
            (id, recipe) -> {
                require(result.putIfAbsent(id, recipe) == null,
                    "No duplicate generated IDs");
            });
        return result;
    }

    private static ItemStack product(MachineRecipe recipe) {
        return ((MachineProduct.ItemProduct) recipe.outputs().get("output")
            .getFirst()).stack();
    }

    @GameTest(template = "empty")
    public static void recoveryCapsuleRetainsOverflowAndUnknownResources(
        GameTestHelper h) {
        var machine = place(h);
        machine.energy.setStored(machine.energy.capacity() - 100);
        var entry = new CompoundTag();
        entry.putString("type", ResourceType.ITEMS.id().toString());
        var data = new CompoundTag();
        data.put("item", new ItemStack(Items.IRON_ORE, 2)
            .save(h.getLevel().registryAccess()));
        entry.put("data", data);
        var unknown = new CompoundTag();
        unknown.putString("type", "example:unknown");
        var unknownData = new CompoundTag();
        unknownData.putInt("amount", 7);
        unknown.put("data", unknownData);
        var entries = new ListTag();
        entries.add(entry);
        entries.add(unknown);
        var payload = new CompoundTag();
        payload.put("resources", entries);
        payload.putLong("refund", 250);
        var capsule = MachineRecoveryItem.create(payload);
        require(MachineRecoveryItem.restore(capsule, machine),
            "Restored compatible resources");
        require(machine.items.getStackInSlot(0).getCount() == 2,
            "Restored items once");
        var remainder = capsule.get(DataComponents.CUSTOM_DATA).copyTag()
            .getCompound("mmdlib_recovery");
        require(remainder.getLong("refund") == 150, "Energy overflow retained");
        require(remainder.getList("resources", Tag.TAG_COMPOUND).getCompound(0)
            .equals(unknown), "Unknown resource retained");
        require(!MachineRecoveryItem.restore(capsule, machine),
            "No progress leaves capsule intact");
        machine.energy.setStored(0);
        require(MachineRecoveryItem.restore(capsule, machine),
            "Remaining energy restored");
        require(machine.energy.stored() == 150 && !capsule.isEmpty(),
            "Unknown payload remains recoverable");
        require(machine.items.getStackInSlot(0).getCount() == 2,
            "No duplicate items on retry");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void resourcePackContainsCompleteCubeAndDropDefaults(
        GameTestHelper h) {
        var client = MachineResourcePack.build(PackType.CLIENT_RESOURCES);
        var server = MachineResourcePack.build(PackType.SERVER_DATA);
        for (String path : List.of("models/block/simple_crusher.json",
            "models/item/simple_crusher.json",
            "blockstates/simple_crusher.json", "lang/en_us.json"))
            require(
                client.containsKey(ResourceLocation
                    .fromNamespaceAndPath("mmdlib_companion", path)),
                "Generated client resource " + path);
        require(
            server.containsKey(ResourceLocation.parse(
                "mmdlib_companion:loot_table/blocks/simple_crusher.json")),
            "Generated loot table");
        require(
            server.containsKey(ResourceLocation
                .parse("minecraft:tags/block/mineable/pickaxe.json")),
            "Generated mining tag");
        h.succeed();
    }
}
