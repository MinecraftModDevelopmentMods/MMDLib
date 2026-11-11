package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.CraftTweakerAPI;
import com.blamejared.crafttweaker.api.action.recipe.ActionAddRecipe;
import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.bracket.custom.RecipeTypeBracketHandler;
import com.blamejared.crafttweaker.api.fluid.IFluidStack;
import com.blamejared.crafttweaker.api.ingredient.IIngredient;
import com.blamejared.crafttweaker.api.item.IItemStack;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.machine.neoforge.MachineContent;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeBuilder;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeEntry;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeType;

/**
 * Adds a recipe to an XML machine's recipe type from an ordinary recipe script,
 * with typed ingredients instead of JSON:
 *
 * <pre>{@code
 * new MachineRecipeBuilder("example:crushing")
 *     .inputItems("input", <item:minecraft:cobblestone>)
 *     .inputEnergy("energy", 2000)
 *     .outputItem("output", <item:minecraft:gravel>)
 *     .time(100)
 *     .build("cobble_to_gravel");
 * }</pre>
 *
 * <p>
 * Groups are the logical input and output group names the machine's XML
 * declares. The recipe type itself, and removing recipes, use CraftTweaker's
 * own {@code <recipetype:example:crushing>} manager.
 */
@ZenRegister
@ZenCodeType.Name("mods.mmdlib.MachineRecipeBuilder")
public final class ScriptMachineRecipeBuilder {
    private final MachineRecipeType type;
    private final MachineRecipeBuilder builder;

    /**
     * Starts a recipe of the XML-declared recipe type {@code recipeType}.
     *
     * @throws IllegalArgumentException
     *             if no machine XML declares it
     */
    @ZenCodeType.Constructor
    public ScriptMachineRecipeBuilder(String recipeType) {
        type = MachineContent.recipeType(ResourceLocation.parse(recipeType));
        builder = MachineRecipeBuilder.recipe(type);
    }

    /** Takes {@code count} of {@code ingredient} from the item group. */
    @ZenCodeType.Method
    public ScriptMachineRecipeBuilder inputItems(String group,
        IIngredient ingredient, @ZenCodeType.OptionalInt(1) int count) {
        builder.inputItems(group, ingredient.asVanillaIngredient(), count);
        return this;
    }

    /** Takes {@code fluid}'s amount, in millibuckets, from the fluid group. */
    @ZenCodeType.Method
    public ScriptMachineRecipeBuilder inputFluid(String group,
        IFluidStack fluid) {
        builder.inputFluid(group, fluid.getFluid(), amount(fluid));
        return this;
    }

    /** Draws {@code energy} over the cycle from the energy group. */
    @ZenCodeType.Method
    public ScriptMachineRecipeBuilder inputEnergy(String group, long energy) {
        builder.inputEnergy(group, energy);
        return this;
    }

    /**
     * Places {@code stack} in the item group, with {@code chance} from 0 to 1.
     */
    @ZenCodeType.Method
    public ScriptMachineRecipeBuilder outputItem(String group, IItemStack stack,
        @ZenCodeType.OptionalFloat(1) float chance) {
        builder.outputItem(group, stack.getInternal().copy(), chance);
        return this;
    }

    /**
     * Places {@code fluid} in the fluid group, with {@code chance} from 0 to 1.
     */
    @ZenCodeType.Method
    public ScriptMachineRecipeBuilder outputFluid(String group,
        IFluidStack fluid, @ZenCodeType.OptionalFloat(1) float chance) {
        builder.outputFluid(group, fluid.getFluid(), amount(fluid), chance);
        return this;
    }

    /** The cycle's length, in ticks. */
    @ZenCodeType.Method
    public ScriptMachineRecipeBuilder time(int ticks) {
        builder.time(ticks);
        return this;
    }

    /**
     * Adds the recipe as {@code name}, like any CraftTweaker recipe.
     *
     * @throws IllegalStateException
     *             if it has no input or no time
     */
    @ZenCodeType.Method
    public void build(String name) {
        var manager = RecipeTypeBracketHandler.getOrDefault(type);
        Recipe<?> entry = new MachineRecipeEntry(type, builder.build());
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
