package zone.moddev.mc.companion.recipe;

import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluids;

import zone.moddev.mc.lib.machine.recipe.generation.*;

/** Mod-owned adapter: the library emits energy recipes; this mod chooses lava as the fuel. */
public final class LavaSmeltingRecipes extends SmeltingRecipeGenerator {
  @Override
  public void generate(RecipeGenerationContext ctx, MachineRecipeOutput output) {
    var p = ctx.properties();
    String group = p.string("fluid-group", "fuel");
    long rate = p.longValue("energy-per-mb", 40, 1, Long.MAX_VALUE);
    var lava =
        ctx.registries()
            .lookupOrThrow(Registries.FLUID)
            .getOrThrow(Fluids.LAVA.builtInRegistryHolder().key());
    super.generate(
        ctx.withProperties(p.without("fluid-group", "energy-per-mb")),
        (id, recipe) ->
            output.accept(
                id, RecipeTransforms.energyToFluid(recipe, group, HolderSet.direct(lava), rate)));
  }
}
