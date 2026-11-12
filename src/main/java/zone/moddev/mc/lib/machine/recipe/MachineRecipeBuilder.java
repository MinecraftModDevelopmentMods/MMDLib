package zone.moddev.mc.lib.machine.recipe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;

import zone.moddev.mc.lib.machine.ingredient.FluidAmount;
import zone.moddev.mc.lib.machine.ingredient.MachineIngredient;

/**
 * Builds machine recipes for datagen, from a {@code RecipeProvider}'s
 * {@code buildRecipes}:
 *
 * <pre>{@code
 * MachineRecipeBuilder.recipe(ExampleRecipes.CRUSHING)
 *     .inputItems("input", Ingredient.of(Tags.Items.ORES_COPPER), 1)
 *     .inputEnergy("energy", 4_000)
 *     .outputItem("output", new ItemStack(Items.RAW_COPPER, 2))
 *     .outputItem("byproduct", new ItemStack(Items.GRAVEL), 0.25F).time(200)
 *     .save(output, ResourceLocation.parse("example:crushing/copper"));
 * }</pre>
 *
 * Groups keep the order they are first named in. Amounts and chances are
 * checked when the recipe is encoded, by the codec that reads recipe files.
 */
public final class MachineRecipeBuilder {
    private final MachineRecipeType type;
    private final Map<String, List<MachineIngredient>> inputs = new LinkedHashMap<>();
    private final Map<String, List<MachineProduct>> outputs = new LinkedHashMap<>();
    private int time;

    private MachineRecipeBuilder(MachineRecipeType type) {
        this.type = type;
    }

    /** A builder for a recipe of {@code type}. */
    public static MachineRecipeBuilder recipe(MachineRecipeType type) {
        return new MachineRecipeBuilder(type);
    }

    /** Adds {@code ingredient} to the inputs of {@code group}. */
    public MachineRecipeBuilder input(String group,
        MachineIngredient ingredient) {
        inputs.computeIfAbsent(group, key -> new ArrayList<>()).add(ingredient);
        return this;
    }

    /** {@code count} items matching {@code items}. */
    public MachineRecipeBuilder inputItems(String group, Ingredient items,
        int count) {
        return input(group, new MachineIngredient.ItemPart(items, count));
    }

    /**
     * {@code millibuckets} of any fluid in {@code fluids}, such as a tag from a
     * registry lookup.
     */
    public MachineRecipeBuilder inputFluids(String group,
        HolderSet<Fluid> fluids, int millibuckets) {
        return input(group,
            new MachineIngredient.FluidPart(fluids, millibuckets));
    }

    /** {@code millibuckets} of {@code fluid}. */
    @SuppressWarnings("deprecation")
    public MachineRecipeBuilder inputFluid(String group, Fluid fluid,
        int millibuckets) {
        return inputFluids(group,
            HolderSet.direct(fluid.builtInRegistryHolder()), millibuckets);
    }

    /** {@code energy} FE over the recipe's time. */
    public MachineRecipeBuilder inputEnergy(String group, long energy) {
        return input(group, new MachineIngredient.EnergyPart(energy));
    }

    /** Adds {@code product} to the outputs of {@code group}. */
    public MachineRecipeBuilder output(String group, MachineProduct product) {
        outputs.computeIfAbsent(group, key -> new ArrayList<>()).add(product);
        return this;
    }

    /** {@code stack}, every run. */
    public MachineRecipeBuilder outputItem(String group, ItemStack stack) {
        return outputItem(group, stack, 1);
    }

    /** {@code stack}, made with {@code chance} in (0, 1]. */
    public MachineRecipeBuilder outputItem(String group, ItemStack stack,
        float chance) {
        return output(group, new MachineProduct.ItemProduct(stack, chance));
    }

    /** {@code millibuckets} of {@code fluid}, every run. */
    public MachineRecipeBuilder outputFluid(String group, Fluid fluid,
        int millibuckets) {
        return outputFluid(group, fluid, millibuckets, 1);
    }

    /**
     * {@code millibuckets} of {@code fluid}, made with {@code chance} in (0,
     * 1].
     */
    @SuppressWarnings("deprecation")
    public MachineRecipeBuilder outputFluid(String group, Fluid fluid,
        int millibuckets, float chance) {
        return output(group,
            new MachineProduct.FluidProduct(
                new FluidAmount(fluid.builtInRegistryHolder(),
                    DataComponentPatch.EMPTY, millibuckets),
                chance));
    }

    /** How long a run takes, in ticks. */
    public MachineRecipeBuilder time(int ticks) {
        time = ticks;
        return this;
    }

    /**
     * The recipe.
     *
     * @throws IllegalStateException
     *             if the time isn't at least 1 or there are no inputs
     */
    public MachineRecipe build() {
        if (time < 1)
            throw new IllegalStateException(
                "A machine recipe needs a time of at least 1");
        if (inputs.isEmpty())
            throw new IllegalStateException("A machine recipe needs an input");
        return new MachineRecipe(inputs, outputs, time);
    }

    /**
     * Builds the recipe and hands it to {@code output} under {@code id},
     * without an advancement.
     */
    public void save(RecipeOutput output, ResourceLocation id) {
        output.accept(id, new MachineRecipeEntry(type, build()), null);
    }
}
