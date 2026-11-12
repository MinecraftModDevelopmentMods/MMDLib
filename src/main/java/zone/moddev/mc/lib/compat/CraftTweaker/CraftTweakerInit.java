package zone.moddev.mc.lib.compat.CraftTweaker;

import net.neoforged.bus.api.IEventBus;

import zone.moddev.mc.lib.api.MaterialExtensions;

/**
 * The only entry point into MMDLib's CraftTweaker integration. MMDLib calls
 * {@link #register} from its constructor, and only when CraftTweaker is loaded,
 * so no CraftTweaker class is touched without it.
 */
public final class CraftTweakerInit {
    private CraftTweakerInit() {
    }

    /**
     * Adds script-built materials, blocks and block modules to MMDLib's
     * definition loading. The scripts run when MMDLib loads definitions: after
     * every mod has constructed, so CraftTweaker is ready, and before registry
     * events, so their content can still be registered. Their models, textures,
     * names, loot tables and tags come from {@link MaterialPacks}.
     */
    public static void register(IEventBus modBus) {
        MaterialExtensions.GLOBAL.registerDefinitionSource(
            "CraftTweaker scripts", ScriptDefinitions::load);
        MaterialPacks.register(modBus);
    }
}
