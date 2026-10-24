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
    public static void publishDefinitions(RegisterEvent event, MaterialFactory.Result result) {
        var material = result.material(); var content = result.content();
        event.register(Registries.MATERIALS_KEY, helper -> helper.register(material.name().orElseThrow(), material));
        event.register(Registries.ITEMS_KEY, helper -> {
            content.items().values().forEach(item -> helper.register(item.name(), item));
            content.extraItems().values().forEach(item -> helper.register(item.name(), item));
            if (content.fluid() != null && content.fluid().bucket() != null)
                helper.register(content.fluid().bucket().name(), content.fluid().bucket());
        });
        event.register(Registries.BLOCKS_KEY, helper -> {
            content.blocks().values().forEach(block -> helper.register(block.name(), block));
            content.extraBlocks().values().forEach(block -> helper.register(block.name(), block));
            if (content.fluid() != null && content.fluid().block() != null)
                helper.register(content.fluid().block().name(), content.fluid().block());
        });
        event.register(Registries.ENTITIES_KEY, helper -> content.entities().values().forEach(entity -> helper.register(entity.name(), entity)));
        event.register(Registries.FLUIDS_KEY, helper -> {
            if (content.fluid() != null) helper.register(content.fluid().name(), content.fluid());
        });
    }
}
