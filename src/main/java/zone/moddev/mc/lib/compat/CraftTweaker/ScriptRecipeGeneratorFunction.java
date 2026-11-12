package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import org.openzen.zencode.java.ZenCodeType;

/**
 * A script function that generates machine recipes on each data reload, given
 * the reload's tags, the generator's properties and a way to add recipes.
 *
 * @docParam this (generation) => {
 *           generation.recipe("example").inputTag("input",
 *           "c:ores/iron").time(100).add(); }
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/machines/RecipeGeneratorFunction")
@ZenCodeType.Name("mods.mmdlib.RecipeGeneratorFunction")
@FunctionalInterface
public interface ScriptRecipeGeneratorFunction {
    /**
     * Generates this reload's recipes.
     *
     * @param generation the reload's tags, the generator's properties, and a
     *            way to add recipes
     * @docParam generation generation
     */
    @ZenCodeType.Method
    void generate(ScriptRecipeGeneration generation);
}
