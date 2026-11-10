package zone.moddev.mc.lib.machine.recipe.generation;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.machine.recipe.MachineRecipe;

/**
 * Stages a definition for the context's type. Does not mutate a live inventory
 * or write files.
 */
@FunctionalInterface
public interface MachineRecipeOutput {
    void accept(ResourceLocation id, MachineRecipe recipe);
}
