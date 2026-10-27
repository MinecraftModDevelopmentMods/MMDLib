package zone.moddev.mc.lib.api;

import net.neoforged.neoforge.registries.RegisterEvent;
import zone.moddev.mc.lib.common.factories.MaterialFactory;
import zone.moddev.mc.lib.util.Registries;

/** Explicit registration-phase hooks for the general MMD definition registries. */
public final class MaterialRegistration {
    private MaterialRegistration() {}
    public static void publishCallbacks(RegisterEvent event, MaterialExtensions extensions) {
        event.register(Registries.EVENT_CALLBACK_KEY, helper -> extensions.events().callbacks().forEach(helper::register));
    }
    /** A standalone block's block and item definitions; its host material is not a material and is not published. */
    public static void publishStandalone(RegisterEvent event, MaterialFactory.Result result) {
        event.register(Registries.ITEMS_KEY, helper -> result.content().itemDefinitions().forEach(helper::register));
        event.register(Registries.BLOCKS_KEY, helper -> result.content().blockDefinitions().forEach(helper::register));
    }
    public static void publishDefinitions(RegisterEvent event, MaterialFactory.Result result) {
        var material = result.material(); var content = result.content();
        event.register(Registries.MATERIALS_KEY, helper -> helper.register(material.name().orElseThrow(), material));
        event.register(Registries.ITEMS_KEY, helper -> content.itemDefinitions().forEach(helper::register));
        event.register(Registries.BLOCKS_KEY, helper -> content.blockDefinitions().forEach(helper::register));
        event.register(Registries.ENTITIES_KEY, helper -> content.entities().values().forEach(entity -> helper.register(entity.name(), entity)));
        event.register(Registries.BLOCK_ENTITIES_KEY, helper -> content.blockEntityDefinitions().forEach(helper::register));
        event.register(Registries.FLUIDS_KEY, helper -> {
            if (content.fluid() != null) helper.register(content.fluid().name(), content.fluid());
        });
    }
}
