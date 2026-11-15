package zone.moddev.mc.lib.machine.neoforge;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.common.crafting.DataComponentIngredient;

import zone.moddev.mc.lib.machine.ingredient.MachineIngredient.EnergyPart;
import zone.moddev.mc.lib.machine.ingredient.MachineIngredient.ItemPart;
import zone.moddev.mc.lib.machine.ingredient.MachineIngredient;
import zone.moddev.mc.lib.machine.recipe.MachineProduct.ItemProduct;
import zone.moddev.mc.lib.machine.recipe.MachineProduct;
import zone.moddev.mc.lib.machine.recipe.MachineRecipe;
import zone.moddev.mc.lib.machine.recipe.RecipeGraph.Operation;

/**
 * Conservative adapters for concrete source stacks. Mods decide which recipe
 * types to offer and supply costs; the library does not register new recipes.
 */
public final class RecipeOperations {
    private RecipeOperations() {
    }

    public record Cost(int ticks, long energy) {
        public Cost {
            if (ticks < 1 || energy < 1)
                throw new IllegalArgumentException("Invalid operation cost");
        }
    }

    public static Optional<Operation> smelting(RecipeHolder<?> holder,
        ItemStack source, Level level, Cost cost) {
        if (source.isEmpty()
            || holder.value().getClass() != SmeltingRecipe.class)
            return Optional.empty();
        var recipe = (SmeltingRecipe) holder.value();
        var input = new SingleRecipeInput(source.copyWithCount(1));
        if (!recipe.matches(input, level) || recipe.getRemainingItems(input)
            .stream().anyMatch(s -> !s.isEmpty()))
            return Optional.empty();
        return operation(holder.id(), "smelt", source, 1,
            recipe.assemble(input, level.registryAccess()), cost);
    }

    /**
     * Supports ordinary shaped/shapeless recipes filled with repeated copies of
     * this concrete source. Special subclasses and remainder recipes are
     * excluded instead of guessing their semantics. Empty shaped cells stay
     * empty, so a 3x2 glass-pane recipe still consumes exactly six glass.
     */
    public static Optional<Operation> crafting(RecipeHolder<?> holder,
        ItemStack source, Level level, Cost cost) {
        if (source.isEmpty()
            || !(holder.value() instanceof CraftingRecipe recipe)
            || (recipe.getClass() != ShapedRecipe.class
                && recipe.getClass() != ShapelessRecipe.class))
            return Optional.empty();
        var ingredients = recipe.getIngredients();
        int width = recipe instanceof ShapedRecipe shaped
            ? shaped.getWidth()
            : 3;
        int height = recipe instanceof ShapedRecipe shaped
            ? shaped.getHeight()
            : 3;
        var slots = NonNullList.withSize(width * height, ItemStack.EMPTY);
        int count = 0;
        for (int i = 0; i < ingredients.size(); i++) {
            var ingredient = ingredients.get(i);
            if (ingredient.isEmpty())
                continue;
            if (i >= slots.size() || !ingredient.test(source))
                return Optional.empty();
            slots.set(i, source.copyWithCount(1));
            count++;
        }
        var input = CraftingInput.of(width, height, slots);
        if (count == 0 || !recipe.matches(input, level) || recipe
            .getRemainingItems(input).stream().anyMatch(s -> !s.isEmpty()))
            return Optional.empty();
        return operation(holder.id(), "craft", source, count,
            recipe.assemble(input, level.registryAccess()), cost);
    }

    /** Adapter for deterministic item transformations supplied by a mod. */
    public static Optional<Operation> machine(ResourceLocation id,
        ResourceLocation kind, MachineRecipe recipe, ItemStack source) {
        if (source.isEmpty() || recipe.time() < 1
            || recipe.outputs().size() != 1
            || recipe.outputs().getOrDefault("output", List.of()).size() != 1
            || !(recipe.outputs().get("output")
                .getFirst() instanceof ItemProduct product)
            || product.chance() != 1 || product.stack().isEmpty()
            || product.stack().getCount() > product.stack().getMaxStackSize())
            return Optional.empty();
        var input = recipe.inputs().getOrDefault("input", List.of());
        if (input.size() != 1 || !(input.getFirst() instanceof ItemPart item)
            || !item.consumed() || item.count() < 1
            || item.count() > source.getMaxStackSize()
            || !item.items().test(source))
            return Optional.empty();
        for (var entry : recipe.inputs().entrySet())
            if (!entry.getKey().equals("input") && entry.getValue().stream()
                .anyMatch(p -> !(p instanceof EnergyPart)))
                return Optional.empty();
        return Optional
            .of(new Operation(id, kind, item.count(), product.stack(), recipe));
    }

    private static Optional<Operation> operation(ResourceLocation id,
        String kind, ItemStack source, int count, ItemStack output, Cost cost) {
        if (output.isEmpty() || output.getCount() > output.getMaxStackSize()
            || count > source.getMaxStackSize())
            return Optional.empty();
        var ingredient = DataComponentIngredient.of(true, source);
        // A source with removed default components may not be representable
        // by the standard strict ingredient; exclude it rather than broaden it.
        if (!ingredient.test(source))
            return Optional.empty();
        Map<String, List<MachineIngredient>> inputs = Map.of("input",
            List.of(new ItemPart(ingredient, count)), "energy",
            List.of(new EnergyPart(cost.energy())));
        Map<String, List<MachineProduct>> outputs = Map.of("output",
            List.of(new ItemProduct(output, 1)));
        return Optional.of(new Operation(id,
            ResourceLocation.fromNamespaceAndPath("mmdlib", kind), count,
            output, new MachineRecipe(inputs, outputs, cost.ticks())));
    }
}
