package zone.moddev.mc.lib.machine.recipe.generation;

/**
 * Implement with a public no-argument constructor; one new instance per type
 * per data reload.
 */
public interface MachineRecipeGenerator {
    void generate(RecipeGenerationContext context, MachineRecipeOutput output);
}
