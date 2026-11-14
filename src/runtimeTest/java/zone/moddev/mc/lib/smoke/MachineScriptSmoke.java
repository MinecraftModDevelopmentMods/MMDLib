package zone.moddev.mc.lib.smoke;

import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

import zone.moddev.mc.lib.machine.ingredient.MachineIngredient;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;
import zone.moddev.mc.lib.machine.recipe.MachineProduct;
import zone.moddev.mc.lib.machine.recipe.MachineRecipe;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeEntry;

/**
 * The test machine {@code mmdlib:test/script_machine} (test resources), whose
 * processor names the test script {@code mmdlib_machines.zs}'s handlers. The
 * start handler copies the glass under the machine one block above it, the
 * finish handler two above, each only for the expected machine and recipe.
 */
final class MachineScriptSmoke {
    private MachineScriptSmoke() {
    }

    static void cycleHandlers(ServerLevel level, BlockPos pos) {
        level.setBlock(pos.below(), Blocks.GLASS.defaultBlockState(), 3);
        level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos.above(2), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos,
            BuiltInRegistries.BLOCK
                .get(ResourceLocation.parse("mmdlib:test/script_machine"))
                .defaultBlockState(),
            3);
        var machine = (DefinedMachineEntity) level.getBlockEntity(pos);
        machine.items.insertItem(0, new ItemStack(Items.STICK), false);
        machine.serverTick();
        require(level.getBlockState(pos.above()).is(Blocks.GLASS),
            "the start handler ran, for the expected machine and recipe");
        require(level.getBlockState(pos.above(2)).isAir(),
            "the finish handler waits for the cycle to finish");
        for (int i = 0; i < 4; i++)
            machine.serverTick();
        require(machine.items.getStackInSlot(1).is(Items.APPLE),
            "the cycle finished: " + machine.items.getStackInSlot(1));
        require(level.getBlockState(pos.above(2)).is(Blocks.GLASS),
            "the finish handler ran, for the expected machine and recipe");
    }

    /**
     * The recipe script {@code machine_recipes.zs}: CraftTweaker's generic
     * manager for the machine's XML-declared recipe type added dirt to diamond
     * and removed the datapack recipe glass to sand, and the typed
     * {@code MachineRecipeBuilder} added two cobblestone to three gravel.
     */
    static void scriptRecipes(ServerLevel level, BlockPos pos) {
        var machine = place(level, pos);
        machine.items.insertItem(0, new ItemStack(Items.DIRT), false);
        for (int i = 0; i < 5; i++)
            machine.serverTick();
        require(machine.items.getStackInSlot(1).is(Items.DIAMOND),
            "the script's recipe ran: " + machine.items.getStackInSlot(1));
        machine.items.extractItem(1, 64, false);
        machine.items.extractItem(1, 64, false);
        machine.items.insertItem(0, new ItemStack(Items.COBBLESTONE, 2), false);
        for (int i = 0; i < 5; i++)
            machine.serverTick();
        require(
            machine.items.getStackInSlot(1).is(Items.GRAVEL)
                && machine.items.getStackInSlot(1).getCount() == 3
                && machine.items.getStackInSlot(0).isEmpty(),
            "the typed recipe took two cobblestone and made three gravel: "
                + machine.items.getStackInSlot(1));
        machine.items.extractItem(1, 64, false);
        // With no recipe using glass, the input filter refuses it outright.
        var refused = machine.items.insertItem(0, new ItemStack(Items.GLASS),
            false);
        require(refused.is(Items.GLASS),
            "the input no longer accepts glass, since no recipe uses it");
        for (int i = 0; i < 5; i++)
            machine.serverTick();
        require(machine.items.getStackInSlot(1).isEmpty(),
            "the removed recipe did not run: "
                + machine.items.getStackInSlot(1));
        reusableAndInvalid(level, machine);
        replaced(place(level, pos.above()));
        handler(level);
    }

    /**
     * The script's Replacer rebuilt the datapack recipe test/flint_to_gunpowder
     * to take clay: clay makes gunpowder, and flint is no longer accepted.
     */
    private static void replaced(DefinedMachineEntity machine) {
        require(machine.items.insertItem(0, new ItemStack(Items.FLINT), false)
            .is(Items.FLINT), "flint is no longer accepted once replaced");
        machine.items.insertItem(0, new ItemStack(Items.CLAY_BALL), false);
        for (int i = 0; i < 5; i++)
            machine.serverTick();
        require(machine.items.getStackInSlot(1).is(Items.GUNPOWDER),
            "the replaced recipe takes clay: "
                + machine.items.getStackInSlot(1));
    }

    /**
     * CraftTweaker finds MMDLib's handler for machine recipes: it dumps the
     * typed builder's recipe as builder calls, and takes apart and rebuilds a
     * recipe with every kind of part unchanged.
     */
    private static void handler(ServerLevel level) {
        var registries = level.registryAccess();
        var holder = level.getRecipeManager()
            .byKey(ResourceLocation.parse("crafttweaker:cobble_to_gravel"))
            .orElseThrow();
        var entry = (MachineRecipeEntry) holder.value();
        var handler = com.blamejared.crafttweaker.api.CraftTweakerAPI
            .getRegistry().getRecipeHandlerFor(entry);
        var manager = com.blamejared.crafttweaker.api.bracket.custom.RecipeTypeBracketHandler
            .getOrDefault(entry.type());
        @SuppressWarnings({"unchecked", "rawtypes"})
        String dump = handler.dumpToCommandString(
            (com.blamejared.crafttweaker.api.recipe.manager.base.IRecipeManager) manager,
            registries, (RecipeHolder) holder);
        require(dump.startsWith(
            "new MachineRecipeBuilder(\"mmdlib:test/script_machine\")")
            && dump.contains(
                ".inputItems(\"input\", <item:minecraft:cobblestone>, 2)")
            && dump.contains(".time(2)"), "dump: " + dump);
        var water = registries.lookupOrThrow(Registries.FLUID)
            .getOrThrow(FluidTags.WATER);
        var full = new MachineRecipe(
            java.util.Map.of("input",
                java.util.List.of(
                    new MachineIngredient.ItemPart(
                        Ingredient.of(Items.IRON_INGOT), 3, false),
                    new MachineIngredient.FluidPart(water, 250, true),
                    new MachineIngredient.EnergyPart(4000))),
            java.util.Map.of("output", java.util.List.of(
                new MachineProduct.ItemProduct(
                    new ItemStack(Items.GOLD_NUGGET, 2), 0.5f),
                new MachineProduct.FluidProduct(
                    new zone.moddev.mc.lib.machine.ingredient.FluidAmount(
                        Fluids.LAVA.builtInRegistryHolder(),
                        net.minecraft.core.component.DataComponentPatch.EMPTY,
                        100),
                    1))),
            7);
        @SuppressWarnings({"unchecked", "rawtypes"})
        var parts = handler.decompose(
            (com.blamejared.crafttweaker.api.recipe.manager.base.IRecipeManager) manager,
            registries, new MachineRecipeEntry(entry.type(), full))
            .orElseThrow();
        @SuppressWarnings({"unchecked", "rawtypes"})
        var rebuilt = (MachineRecipeEntry) handler.recompose(
            (com.blamejared.crafttweaker.api.recipe.manager.base.IRecipeManager) manager,
            registries,
            (com.blamejared.crafttweaker.api.recipe.component.IDecomposedRecipe) parts)
            .orElseThrow();
        // CraftTweaker rebuilds item ingredients as its own ingredient type,
        // as for every recipe it rebuilds: compare what they match, and the
        // rest as JSON.
        var items = (MachineIngredient.ItemPart) rebuilt.recipe().inputs()
            .get("input").getFirst();
        require(
            items.items().test(new ItemStack(Items.IRON_INGOT))
                && !items.items().test(new ItemStack(Items.GOLD_INGOT))
                && items.count() == 3 && !items.consumed(),
            "the item input survives the round trip: " + items);
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        var before = withoutItems(
            MachineRecipe.CODEC.codec().encodeStart(ops, full).getOrThrow());
        var after = withoutItems(MachineRecipe.CODEC.codec()
            .encodeStart(ops, rebuilt.recipe()).getOrThrow());
        require(before.equals(after),
            "round trip kept the recipe: " + before + " -> " + after);
    }

    private static com.google.gson.JsonElement withoutItems(
        com.google.gson.JsonElement recipe) {
        recipe.getAsJsonObject().getAsJsonObject("inputs")
            .getAsJsonArray("input").get(0).getAsJsonObject().remove("items");
        return recipe;
    }

    /**
     * The typed builder's reusable input keeps the bone, and its recipe with an
     * unknown group was refused.
     */
    private static void reusableAndInvalid(ServerLevel level,
        DefinedMachineEntity machine) {
        machine.items.insertItem(0, new ItemStack(Items.BONE), false);
        for (int i = 0; i < 5; i++)
            machine.serverTick();
        require(
            machine.items.getStackInSlot(1).is(Items.BONE_MEAL)
                && machine.items.getStackInSlot(0).is(Items.BONE),
            "the reusable bone stayed and made bone meal: "
                + machine.items.getStackInSlot(0) + " -> "
                + machine.items.getStackInSlot(1));
        require(level.getRecipeManager()
            .byKey(ResourceLocation.parse("crafttweaker:unknown_group"))
            .isEmpty(), "the recipe with an unknown group was refused");
    }

    /**
     * The test script's recipe generator, named by test/generated_machine's
     * XML, made an ingot-to-nuggets recipe for each metal with both tags, with
     * the XML's nugget count: an iron ingot makes four iron nuggets.
     */
    static void generatedRecipes(ServerLevel level, BlockPos pos) {
        require(level.getRecipeManager()
            .byKey(ResourceLocation.parse("mmdlib:ingot_nuggets/iron"))
            .isPresent(), "the generator made mmdlib:ingot_nuggets/iron");
        level.setBlock(pos,
            BuiltInRegistries.BLOCK
                .get(ResourceLocation.parse("mmdlib:test/generated_machine"))
                .defaultBlockState(),
            3);
        var machine = (DefinedMachineEntity) level.getBlockEntity(pos);
        machine.items.insertItem(0, new ItemStack(Items.IRON_INGOT), false);
        for (int i = 0; i < 5; i++)
            machine.serverTick();
        var made = machine.items.getStackInSlot(1);
        require(
            made.getCount() == 4
                && made.is(net.minecraft.tags.TagKey.create(Registries.ITEM,
                    ResourceLocation.parse("c:nuggets/iron"))),
            "an iron ingot made four iron nuggets: " + made);
    }

    private static DefinedMachineEntity place(ServerLevel level, BlockPos pos) {
        level.setBlock(pos,
            BuiltInRegistries.BLOCK
                .get(ResourceLocation.parse("mmdlib:test/script_machine"))
                .defaultBlockState(),
            3);
        return (DefinedMachineEntity) level.getBlockEntity(pos);
    }

    private static void require(boolean condition, String what) {
        if (!condition)
            throw new AssertionError("Machine script smoke failed: " + what);
    }
}
