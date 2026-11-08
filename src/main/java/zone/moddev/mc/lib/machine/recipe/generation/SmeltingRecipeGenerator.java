package zone.moddev.mc.lib.machine.recipe.generation;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.SmeltingRecipe;

import zone.moddev.mc.lib.machine.recipe.MachineRecipeBuilder;

/** Adapts existing smelting recipes, preserving their ingredients, outputs and cooking times. */
public class SmeltingRecipeGenerator implements MachineRecipeGenerator {
  public static final long DEFAULT_ENERGY_PER_TICK = 20;

  @Override
  public void generate(RecipeGenerationContext ctx, MachineRecipeOutput output) {
    var p = ctx.properties();
    p.requireOnly("input-group", "output-group", "energy-group", "energy-per-tick");
    String input = p.string("input-group", "input");
    String result = p.string("output-group", "output");
    String power = p.string("energy-group", "energy");
    long rate = p.longValue("energy-per-tick", DEFAULT_ENERGY_PER_TICK, 1, Long.MAX_VALUE);
    for (var holder : ctx.recipes()) {
      if (!(holder.value() instanceof SmeltingRecipe recipe)) continue;
      var product = recipe.getResultItem(ctx.registries());
      if (product.isEmpty() || recipe.getIngredients().size() != 1 || recipe.getCookingTime() < 1)
        throw p.error("Invalid smelting recipe " + holder.id());
      long energy;
      try {
        energy = Math.multiplyExact(rate, recipe.getCookingTime());
      } catch (ArithmeticException ex) {
        throw p.error("Energy cost overflows for " + holder.id());
      }
      var id =
          ResourceLocation.fromNamespaceAndPath(
              ctx.recipeType().id().getNamespace(),
              "generated/"
                  + ctx.recipeType().id().getPath()
                  + "/smelting/"
                  + holder.id().getNamespace()
                  + "/"
                  + holder.id().getPath());
      output.accept(
          id,
          MachineRecipeBuilder.recipe(ctx.recipeType())
              .inputItems(input, recipe.getIngredients().getFirst(), 1)
              .inputEnergy(power, energy)
              .outputItem(result, product.copy())
              .time(recipe.getCookingTime())
              .build());
    }
  }
}
