package zone.moddev.mc.lib.machine.recipe;

import java.util.List;
import java.util.Objects;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import zone.moddev.mc.lib.machine.ingredient.MachineIngredient;

/**
 * A {@link MachineRecipe} as a vanilla recipe of a {@link MachineRecipeType}. It matches when
 * {@link RecipeMatcher#check} passes: every input present and room for every product.
 */
public record MachineRecipeEntry(MachineRecipeType type, MachineRecipe recipe)
    implements Recipe<MachineRecipeInput> {
  public MachineRecipeEntry {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(recipe, "recipe");
  }

  @Override
  public boolean matches(MachineRecipeInput input, Level level) {
    return RecipeMatcher.check(recipe, input.inventory()).ok();
  }

  /** The first item product; machines make their products through {@link RecipeMatcher}. */
  @Override
  public ItemStack assemble(MachineRecipeInput input, HolderLookup.Provider registries) {
    return getResultItem(registries);
  }

  @Override
  public boolean canCraftInDimensions(int width, int height) {
    return true;
  }

  /** A copy of the first item product, or empty if the recipe makes only fluids. */
  @Override
  public ItemStack getResultItem(HolderLookup.Provider registries) {
    return recipe.outputs().values().stream()
        .flatMap(List::stream)
        .filter(MachineProduct.ItemProduct.class::isInstance)
        .map(product -> ((MachineProduct.ItemProduct) product).stack())
        .findFirst()
        .orElse(ItemStack.EMPTY);
  }

  /** The item inputs, in group order. */
  @Override
  public NonNullList<Ingredient> getIngredients() {
    var result = NonNullList.<Ingredient>create();
    recipe.inputs().values().stream()
        .flatMap(List::stream)
        .filter(MachineIngredient.ItemPart.class::isInstance)
        .forEach(part -> result.add(((MachineIngredient.ItemPart) part).items()));
    return result;
  }

  /** Machine recipes stay out of the recipe book. */
  @Override
  public boolean isSpecial() {
    return true;
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return type.serializer();
  }

  @Override
  public RecipeType<?> getType() {
    return type;
  }
}
