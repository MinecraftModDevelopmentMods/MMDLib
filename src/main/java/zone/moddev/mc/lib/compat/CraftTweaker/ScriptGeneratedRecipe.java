package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.fluid.IFluidStack;
import com.blamejared.crafttweaker.api.ingredient.IIngredient;
import com.blamejared.crafttweaker.api.item.IItemStack;

import net.minecraft.core.HolderSet;
import net.minecraft.resources.ResourceLocation;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.machine.ingredient.MachineIngredient;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeBuilder;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeType;
import zone.moddev.mc.lib.machine.recipe.generation.MachineRecipeOutput;
import zone.moddev.mc.lib.machine.recipe.generation.ReloadTags;

/**
 * One recipe a script recipe generator adds, built like
 * {@code MachineRecipeBuilder} and added with {@link #add()}. Item tags come
 * from the reload, with {@link #inputTag}.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.GeneratedRecipe")
public final class ScriptGeneratedRecipe {
    private final MachineRecipeBuilder builder;
    private final ReloadTags tags;
    private final ResourceLocation id;
    private final MachineRecipeOutput output;

    ScriptGeneratedRecipe(MachineRecipeType type, ReloadTags tags,
        ResourceLocation id, MachineRecipeOutput output) {
        builder = MachineRecipeBuilder.recipe(type);
        this.tags = tags;
        this.id = id;
        this.output = output;
    }

    /**
     * Takes {@code count} of {@code ingredient}; with {@code consumed} false,
     * only requires and reserves them.
     */
    @ZenCodeType.Method
    public ScriptGeneratedRecipe inputItems(String group,
        IIngredient ingredient, @ZenCodeType.OptionalInt(1) int count,
        @ZenCodeType.OptionalBoolean(true) boolean consumed) {
        builder.input(group, new MachineIngredient.ItemPart(
            ingredient.asVanillaIngredient(), count, consumed));
        return this;
    }

    /**
     * Takes {@code count} items of the item tag {@code tag}, as of this reload,
     * such as {@code "c:ores/iron"}.
     *
     * @throws IllegalArgumentException
     *             if the tag has no items
     */
    @ZenCodeType.Method
    public ScriptGeneratedRecipe inputTag(String group, String tag,
        @ZenCodeType.OptionalInt(1) int count,
        @ZenCodeType.OptionalBoolean(true) boolean consumed) {
        var location = ResourceLocation.parse(tag);
        if (tags.members(location).isEmpty())
            throw new IllegalArgumentException(
                "Item tag " + tag + " has no items in this reload");
        builder.input(group, new MachineIngredient.ItemPart(
            tags.ingredient(location), count, consumed));
        return this;
    }

    /** Takes {@code fluid}'s amount, in millibuckets. */
    @ZenCodeType.Method
    public ScriptGeneratedRecipe inputFluid(String group, IFluidStack fluid,
        @ZenCodeType.OptionalBoolean(true) boolean consumed) {
        builder.input(group,
            new MachineIngredient.FluidPart(
                HolderSet.direct(fluid.getFluid().builtInRegistryHolder()),
                (int) fluid.getAmount(), consumed));
        return this;
    }

    /** Draws {@code energy} over the cycle. */
    @ZenCodeType.Method
    public ScriptGeneratedRecipe inputEnergy(String group, long energy) {
        builder.inputEnergy(group, energy);
        return this;
    }

    /** Places {@code stack}, with {@code chance} from 0 to 1. */
    @ZenCodeType.Method
    public ScriptGeneratedRecipe outputItem(String group, IItemStack stack,
        @ZenCodeType.OptionalFloat(1) float chance) {
        builder.outputItem(group, stack.getInternal().copy(), chance);
        return this;
    }

    /** Places {@code fluid}, with {@code chance} from 0 to 1. */
    @ZenCodeType.Method
    public ScriptGeneratedRecipe outputFluid(String group, IFluidStack fluid,
        @ZenCodeType.OptionalFloat(1) float chance) {
        builder.outputFluid(group, fluid.getFluid(), (int) fluid.getAmount(),
            chance);
        return this;
    }

    /** The cycle's length, in ticks. */
    @ZenCodeType.Method
    public ScriptGeneratedRecipe time(int ticks) {
        builder.time(ticks);
        return this;
    }

    /**
     * Adds the recipe to this reload. A recipe file with the same ID replaces
     * it, and the machines' groups are checked as for any generated recipe.
     *
     * @throws IllegalStateException
     *             if it has no input or no time
     */
    @ZenCodeType.Method
    public void add() {
        output.accept(id, builder.build());
    }
}
