package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;

import org.openzen.zencode.java.ZenCodeType;

/**
 * A script function that generates machine recipes on each data reload, given
 * the reload's tags, the generator's properties and a way to add recipes.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.RecipeGeneratorFunction")
@FunctionalInterface
public interface ScriptRecipeGeneratorFunction {
    @ZenCodeType.Method
    void generate(ScriptRecipeGeneration generation);
}
