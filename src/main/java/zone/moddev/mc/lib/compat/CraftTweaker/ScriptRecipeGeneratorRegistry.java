package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import org.openzen.zencode.java.ZenCodeType;

/**
 * Registers script functions as machine recipe generators, which machine XML
 * runs through {@link ScriptRecipeGenerator}.
 *
 * <p>
 * Generators are registered once, at startup, and run on every data reload:
 * tags come from the reload, as {@code <tag:...>} brackets are not usable then.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/machines/RecipeGenerators")
@ZenCodeType.Name("mods.mmdlib.RecipeGenerators")
public final class ScriptRecipeGeneratorRegistry {
    private ScriptRecipeGeneratorRegistry() {
    }

    /**
     * Registers {@code generator} as {@code mmdlib:<name>}, for machine XML to
     * name in its generator's {@code script} attribute. It is an error if the
     * ID is already registered.
     *
     * @param name the name; the ID is {@code mmdlib:<name>}
     * @param generator adds the recipes on each reload
     * @return the generator's ID, for the machine XML's {@code script}
     *         attribute
     * @docParam name "ore_dusts"
     * @docParam generator (gen) => { gen.recipe("iron").inputTag("input",
     *           "c:ores/iron").time(100).add(); }
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

    /**
     * Whether a script registered the generator {@code id}.
     *
     * @param id the generator ID
     * @return true if registered
     * @docParam id "mmdlib:ore_dusts"
     */
    @ZenCodeType.Method
    public static boolean exists(String id) {
        return ScriptRecipeGenerators
            .get(net.minecraft.resources.ResourceLocation.parse(id))
            .isPresent();
    }
}
