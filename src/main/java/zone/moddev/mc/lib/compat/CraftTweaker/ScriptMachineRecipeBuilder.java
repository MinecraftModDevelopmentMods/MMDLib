package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.CraftTweakerAPI;
import com.blamejared.crafttweaker.api.action.recipe.ActionAddRecipe;
import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.bracket.custom.RecipeTypeBracketHandler;
import com.blamejared.crafttweaker.api.fluid.CTFluidIngredient;
import com.blamejared.crafttweaker.api.fluid.IFluidStack;
import com.blamejared.crafttweaker.api.ingredient.IIngredient;
import com.blamejared.crafttweaker.api.item.IItemStack;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.machine.ingredient.MachineIngredient;
import zone.moddev.mc.lib.machine.neoforge.GeneratedMachineRecipes;
import zone.moddev.mc.lib.machine.neoforge.MachineContent;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeBuilder;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeEntry;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeType;

/**
 * Adds a recipe to an XML machine's recipe type from an ordinary recipe script
 * (the default {@code crafttweaker} loader), with typed ingredients instead of
 * JSON. Groups are the logical input and output group names the machine's XML
 * declares. The recipe type itself, and removing recipes, use CraftTweaker's
 * own {@code <recipetype:...>} manager.
 *
 * @docParam this new MachineRecipeBuilder("example:crushing")
 */
@ZenRegister
@Document("mods/MMDLib/machines/MachineRecipeBuilder")
@ZenCodeType.Name("mods.mmdlib.MachineRecipeBuilder")
public final class ScriptMachineRecipeBuilder {
    private final MachineRecipeType type;
    private final MachineRecipeBuilder builder;

    /**
     * Starts a recipe of the XML-declared recipe type {@code recipeType}. It is
     * an error if no machine XML declares it.
     *
     * @param recipeType the recipe type's ID
     * @docParam recipeType "example:crushing"
     */
    @ZenCodeType.Constructor
    public ScriptMachineRecipeBuilder(String recipeType) {
        type = MachineContent.recipeType(ResourceLocation.parse(recipeType));
        builder = MachineRecipeBuilder.recipe(type);
    }

    /**
     * Takes {@code count} of {@code ingredient} from the item group; with
     * {@code consumed} false, only requires and reserves them, as a reusable
     * input.
     *
     * @param group the group name the machine XML declares
     * @param ingredient the items to take
     * @param count how many
     * @param consumed false to only require and reserve them
     * @return this builder
     * @docParam group "input"
     * @docParam ingredient <item:minecraft:cobblestone>
     * @docParam count 2
     * @docParam consumed true
     */
    @ZenCodeType.Method
    public ScriptMachineRecipeBuilder inputItems(String group,
        IIngredient ingredient, @ZenCodeType.OptionalInt(1) int count,
        @ZenCodeType.OptionalBoolean(true) boolean consumed) {
        builder.input(group, new MachineIngredient.ItemPart(
            ingredient.asVanillaIngredient(), count, consumed));
        return this;
    }

    /**
     * Takes {@code fluid}'s amount, in millibuckets, from the fluid group; with
     * {@code consumed} false, only requires and reserves it.
     *
     * @param group the fluid group name
     * @param fluid the fluid and amount, in millibuckets
     * @param consumed false to only require and reserve them
     * @return this builder
     * @docParam group "fluids"
     * @docParam fluid <fluid:minecraft:water> * 250
     * @docParam consumed true
     */
    @ZenCodeType.Method
    public ScriptMachineRecipeBuilder inputFluid(String group,
        IFluidStack fluid,
        @ZenCodeType.OptionalBoolean(true) boolean consumed) {
        builder.input(group,
            new MachineIngredient.FluidPart(
                HolderSet.direct(fluid.getFluid().builtInRegistryHolder()),
                amount(fluid), consumed));
        return this;
    }

    /**
     * Takes one of several fluids, or a tagged fluid, from the fluid group,
     * such as {@code <tag:fluids:minecraft:water> * 250}; every choice has the
     * same amount. With {@code consumed} false, only requires and reserves it.
     * It is an error if it mixes a tag with other choices, or its amounts
     * differ.
     *
     * @param group the fluid group name
     * @param fluids the fluids or fluid tag, with the amount in millibuckets
     * @param consumed false to only require and reserve them
     * @return this builder
     * @docParam group "fluids"
     * @docParam fluids <tag:fluids:minecraft:water> * 250
     * @docParam consumed true
     */
    @ZenCodeType.Method
    public ScriptMachineRecipeBuilder inputFluids(String group,
        CTFluidIngredient fluids,
        @ZenCodeType.OptionalBoolean(true) boolean consumed) {
        builder.input(group, MachineRecipeHandler.fluidPart(fluids, consumed,
            CraftTweakerAPI.getAccessibleElementsProvider().registryAccess()));
        return this;
    }

    /**
     * Draws {@code energy} over the cycle from the energy group.
     *
     * @param group the energy group name the machine XML declares
     * @param energy the energy, in FE
     * @return this builder
     * @docParam group "energy"
     * @docParam energy 2000
     */
    @ZenCodeType.Method
    public ScriptMachineRecipeBuilder inputEnergy(String group, long energy) {
        builder.inputEnergy(group, energy);
        return this;
    }

    /**
     * Places {@code stack} in the item group, with {@code chance} from 0 to 1.
     *
     * @param group the group name the machine XML declares
     * @param stack the item and count
     * @param chance the chance it is made, above 0 and up to 1
     * @return this builder
     * @docParam group "output"
     * @docParam stack <item:minecraft:gravel> * 3
     * @docParam chance 0.5
     */
    @ZenCodeType.Method
    public ScriptMachineRecipeBuilder outputItem(String group, IItemStack stack,
        @ZenCodeType.OptionalFloat(1) float chance) {
        builder.outputItem(group, stack.getInternal().copy(), chance);
        return this;
    }

    /**
     * Places {@code fluid} in the fluid group, with {@code chance} from 0 to 1.
     *
     * @param group the fluid group name
     * @param fluid the fluid and amount, in millibuckets
     * @param chance the chance it is made, above 0 and up to 1
     * @return this builder
     * @docParam group "fluids"
     * @docParam fluid <fluid:minecraft:lava> * 100
     * @docParam chance 0.5
     */
    @ZenCodeType.Method
    public ScriptMachineRecipeBuilder outputFluid(String group,
        IFluidStack fluid, @ZenCodeType.OptionalFloat(1) float chance) {
        builder.outputFluid(group, fluid.getFluid(), amount(fluid), chance);
        return this;
    }

    /**
     * Sets the cycle's length; required.
     *
     * @param ticks the length, in ticks
     * @return this builder
     * @docParam ticks 100
     */
    @ZenCodeType.Method
    public ScriptMachineRecipeBuilder time(int ticks) {
        builder.time(ticks);
        return this;
    }

    /**
     * Adds the recipe as {@code name}, like any CraftTweaker recipe. It is an
     * error if it has no input or no time, or if a machine using the type has
     * no such group, or a group holds another kind of resource, as for datapack
     * recipes.
     *
     * @param name the recipe's name, as for any CraftTweaker recipe
     * @docParam name "cobble_to_gravel"
     */
    @ZenCodeType.Method
    public void build(String name) {
        var manager = RecipeTypeBracketHandler.getOrDefault(type);
        var recipe = builder.build();
        try {
            GeneratedMachineRecipes.validateGroups(type, recipe);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(
                "Machine recipe " + name + ": " + error.getMessage(), error);
        }
        Recipe<?> entry = new MachineRecipeEntry(type, recipe);
        CraftTweakerAPI.apply(new ActionAddRecipe<>(manager,
            manager.createHolder(manager.fixRecipeId(name), entry)));
    }

    private static int amount(IFluidStack fluid) {
        long amount = fluid.getAmount();
        if (amount < 1 || amount > Integer.MAX_VALUE)
            throw new IllegalArgumentException("A fluid amount must be 1 to "
                + Integer.MAX_VALUE + " millibuckets, got " + amount);
        return (int) amount;
    }
}
