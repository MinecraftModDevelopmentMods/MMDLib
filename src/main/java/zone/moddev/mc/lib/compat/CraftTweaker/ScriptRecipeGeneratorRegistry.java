package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;

import org.openzen.zencode.java.ZenCodeType;

/**
 * Registers script functions as machine recipe generators, which machine XML
 * runs through {@link ScriptRecipeGenerator}:
 *
 * <pre>{@code
 * #loader mmdlib
 * import mods.mmdlib.RecipeGenerators;
 *
 * RecipeGenerators.register("ore_dusts", (gen) => {
 *     for tag in gen.tagIds("c:ores/") {
 *         val metal = tag[7 .. tag.length];
 *         if gen.hasTag("c:dusts/" + metal) {
 *             gen.recipe("ore_to_dust/" + metal)
 *                 .inputTag("input", tag)
 *                 .outputItem("output", gen.tagItems("c:dusts/" + metal)[0] * 2)
 *                 .time(gen.intProperty("time", 100))
 *                 .add();
 *         }
 *     }
 * });
 * }</pre>
 *
 * <p>
 * Generators are registered once, at startup, and run on every data reload:
 * tags come from the reload, as {@code <tag:...>} brackets are not usable then.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.RecipeGenerators")
public final class ScriptRecipeGeneratorRegistry {
    private ScriptRecipeGeneratorRegistry() {
    }

    /**
     * Registers {@code generator} as {@code mmdlib:<name>}, for machine XML to
     * name in its generator's {@code script} attribute.
     *
     * @return the generator's ID
     * @throws IllegalArgumentException
     *             if the ID is already registered
     */
    @ZenCodeType.Method
    public static String register(String name,
        ScriptRecipeGeneratorFunction generator) {
        ScriptDefinitions.catalog();
        var id = ScriptDefinitionProperties.mmdlibId(name, "recipe generator");
        ScriptRecipeGenerators.register(id, (context, output) -> generator
            .generate(new ScriptRecipeGeneration(context, output)));
        return id.toString();
    }

    /** Whether a script registered the generator {@code id}. */
    @ZenCodeType.Method
    public static boolean exists(String id) {
        return ScriptRecipeGenerators
            .get(net.minecraft.resources.ResourceLocation.parse(id))
            .isPresent();
    }
}
