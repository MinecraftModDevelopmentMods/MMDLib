package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.plugin.CraftTweakerPlugin;
import com.blamejared.crafttweaker.api.plugin.ICraftTweakerPlugin;
import com.blamejared.crafttweaker.api.plugin.ILoaderRegistrationHandler;
import com.blamejared.crafttweaker.api.plugin.IRecipeComponentRegistrationHandler;
import com.blamejared.crafttweaker.api.plugin.IRecipeHandlerRegistrationHandler;
import com.blamejared.crafttweaker.api.plugin.IScriptLoadSourceRegistrationHandler;
import com.blamejared.crafttweaker.api.plugin.IScriptRunModuleConfiguratorRegistrationHandler;
import com.blamejared.crafttweaker.api.zencode.scriptrun.IScriptRunModuleConfigurator;

import zone.moddev.mc.lib.machine.recipe.MachineRecipeEntry;

/**
 * Registers MMDLib's script loader and load source, and the recipe handler for
 * XML machine recipes, with CraftTweaker. CraftTweaker finds this class by its
 * annotation while it constructs, so nothing in MMDLib refers to it.
 */
@CraftTweakerPlugin("mmdlib:main")
public final class MMDLibCraftTweakerPlugin implements ICraftTweakerPlugin {
    @Override
    public void registerLoaders(ILoaderRegistrationHandler handler) {
        // Inheriting crafttweaker makes its types (IData, item stacks,
        // entities...) usable in
        // #loader mmdlib scripts.
        handler.registerLoader(ScriptDefinitions.LOADER, "crafttweaker");
    }

    @Override
    public void registerModuleConfigurators(
        IScriptRunModuleConfiguratorRegistrationHandler handler) {
        // Every loader needs one; the default gives scripts CraftTweaker's
        // standard modules.
        handler.registerConfigurator(ScriptDefinitions.LOADER,
            IScriptRunModuleConfigurator.createDefault("crafttweaker"));
    }

    @Override
    public void registerRecipeComponents(
        IRecipeComponentRegistrationHandler handler) {
        handler.registerRecipeComponent(MachineRecipeComponents.LAYOUT);
        handler.registerRecipeComponent(MachineRecipeComponents.ENERGY);
    }

    /**
     * Dumps, and ingredient replacement, for every XML machine recipe type:
     * they share one recipe class.
     */
    @Override
    public void registerRecipeHandlers(
        IRecipeHandlerRegistrationHandler handler) {
        handler.registerRecipeHandler(MachineRecipeEntry.class,
            new MachineRecipeHandler());
    }

    @Override
    public void registerLoadSource(
        IScriptLoadSourceRegistrationHandler handler) {
        handler.registerLoadSource(ScriptDefinitions.LOAD_SOURCE);
    }
}
