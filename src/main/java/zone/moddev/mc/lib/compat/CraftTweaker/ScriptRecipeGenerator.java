package zone.moddev.mc.lib.compat.CraftTweaker;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.machine.recipe.generation.MachineRecipeGenerator;
import zone.moddev.mc.lib.machine.recipe.generation.MachineRecipeOutput;
import zone.moddev.mc.lib.machine.recipe.generation.RecipeGenerationContext;

/**
 * The recipe generator class machine XML names to run a script's generator:
 *
 * <pre>{@code
 * <recipe-type id="example:crushing" declare="true" name="Crushing">
 *   <generator class=
"zone.moddev.mc.lib.compat.CraftTweaker.ScriptRecipeGenerator"
 *              script="mmdlib:ore_dusts" time="120"/>
 * </recipe-type>
 * }</pre>
 *
 * <p>
 * {@code script} names a generator a {@code #loader mmdlib} script registered
 * with {@code mods.mmdlib.RecipeGenerators.register}; the generator's other
 * attributes are its properties. Like any generator, it runs on every data
 * reload, and an error fails that reload.
 */
public final class ScriptRecipeGenerator implements MachineRecipeGenerator {
    /** Created by the machine runtime, once per type per reload. */
    public ScriptRecipeGenerator() {
    }

    @Override
    public void generate(RecipeGenerationContext context,
        MachineRecipeOutput output) {
        var properties = context.properties();
        var id = ResourceLocation.parse(properties.requiredString("script"));
        var generator = ScriptRecipeGenerators.get(id)
            .orElseThrow(() -> properties.error("No script recipe generator "
                + id + "; register it with mods.mmdlib.RecipeGenerators in a"
                + " #loader mmdlib script (CraftTweaker must be installed)"));
        generator.generate(context.withProperties(properties.without("script")),
            output);
    }
}
