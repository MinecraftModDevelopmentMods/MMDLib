package zone.moddev.mc.lib.compat.CraftTweaker.crusher;

import com.blamejared.crafttweaker.api.CraftTweakerAPI;
import com.blamejared.crafttweaker.api.action.recipe.ActionAddRecipe;
import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.ingredient.IIngredient;
import com.blamejared.crafttweaker.api.item.IItemStack;
import com.blamejared.crafttweaker.api.recipe.manager.base.IRecipeManager;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import net.minecraft.world.item.crafting.RecipeType;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.common.recipes.CrushingRecipe;

/**
 * The crushing recipe type's manager, {@code <recipetype:mmdlib:crushing>}, for
 * ordinary recipe scripts (the default {@code crafttweaker} loader). Removing
 * and querying recipes use CraftTweaker's usual recipe manager methods.
 *
 * @docParam this <recipetype:mmdlib:crushing>
 */
@ZenRegister
@Document("mods/MMDLib/crusher/CrushingRecipeManager")
@ZenCodeType.Name("mods.mmdlib.crusher.CrushingRecipeManager")
public final class CrushingRecipeManager
    implements IRecipeManager<CrushingRecipe> {
    /**
     * Adds a crushing recipe: one input item crushed into the output. The input
     * must be a single item with no transformers.
     *
     * @param name the recipe's name, as for any CraftTweaker recipe
     * @param output what crushing makes
     * @param input the item crushed
     * @docParam name "cobble_to_gravel"
     * @docParam output <item:minecraft:gravel> * 2
     * @docParam input <item:minecraft:cobblestone>
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
