package zone.moddev.mc.lib.machine.recipe.generation;

import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.crafting.RecipeHolder;

import zone.moddev.mc.lib.machine.recipe.MachineRecipeType;

/** Reload data for a recipe type shared by all its machines; no live machine inventory. */
public record RecipeGenerationContext(
    MachineRecipeType recipeType,
    HolderLookup.Provider registries,
    ReloadTags tags,
    List<RecipeHolder<?>> recipes,
    GeneratorProperties properties) {
  public RecipeGenerationContext {
    recipes = List.copyOf(recipes);
  }
}
