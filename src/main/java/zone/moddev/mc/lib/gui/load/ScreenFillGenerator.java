package zone.moddev.mc.lib.gui.load;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

/** Supplies ordinary screen elements for a generated fill or placeholder. */
@FunctionalInterface
public interface ScreenFillGenerator {
    ScreenFillGenerator UNAVAILABLE = (id, node) -> {
        throw new ScreenException(node.source(), node.line(),
            "No generator context for " + id);
    };

    List<ScreenReader.Node> generate(ResourceLocation screen,
        ScreenReader.Node template);

    /**
     * Contextual filter for literal placeholder defaults. An authored fill
     * replaces the placeholder's attributes, so this never filters an
     * overriding fill. Screens without a contextual provider retain their
     * literal defaults.
     */
    default boolean includeDefault(ResourceLocation screen,
        ScreenReader.Node template) {
        return true;
    }
}
