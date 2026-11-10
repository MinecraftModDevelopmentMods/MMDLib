package zone.moddev.mc.lib.compat.CraftTweaker.crusher;

import com.blamejared.crafttweaker.api.CraftTweakerAPI;
import com.blamejared.crafttweaker.api.action.recipe.ActionAddRecipe;
import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.ingredient.IIngredient;
import com.blamejared.crafttweaker.api.item.IItemStack;
import com.blamejared.crafttweaker.api.recipe.manager.base.IRecipeManager;

import net.minecraft.world.item.crafting.RecipeType;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.common.recipes.CrushingRecipe;

/**
 * Normal recipe-loader integration, independent of the mmdlib startup content
 * loader.
 */
@ZenRegister
@ZenCodeType.Name("mods.mmdlib.crusher.CrushingRecipeManager")
public final class CrushingRecipeManager
    implements IRecipeManager<CrushingRecipe> {
    /**
     * Add a one-input crushing recipe, using standard CraftTweaker recipe IDs
     * and actions. Ingredient amounts greater than one are not supported by
     * this recipe type.
     */
    @ZenCodeType.Method
    public void addRecipe(String name, IItemStack output, IIngredient input) {
        if (input.asIIngredientWithAmount().amount() != 1)
            throw new IllegalArgumentException(
                "Crushing recipes consume exactly one input item");
        if (!input.transformers().isEmpty())
            throw new IllegalArgumentException(
                "Crushing recipes do not support crafting ingredient transformers");
        var ingredient = input.asVanillaIngredient();
        if (ingredient.isEmpty())
            throw new IllegalArgumentException(
                "Crushing input must not be empty");
        var recipe = new CrushingRecipe(ingredient, output.getInternal());
        CraftTweakerAPI.apply(new ActionAddRecipe<>(this,
            createHolder(fixRecipeId(name), recipe)));
    }

    @Override
    public RecipeType<CrushingRecipe> getRecipeType() {
        return CrushingRecipe.TYPE.get();
    }
}
