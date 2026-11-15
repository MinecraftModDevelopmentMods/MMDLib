package zone.moddev.mc.lib.gui.client.recipe;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Client recipe choices. Selection implementations must submit a
 * server-validated action.
 */
public interface RecipeCatalog {
    record Choice(String key, RecipePresentation presentation) {
    }
    record Group(String key, Component title, ItemStack icon,
        List<Choice> choices) {
        public Group {
            icon = icon.copy();
            choices = List.copyOf(choices);
        }
    }

    /** Return a stable immutable list until the recipe catalog changes. */
    List<Group> groups();

    /** Initial group when the drawer has no remembered selection. */
    default String initialGroup() {
        return "";
    }

    /**
     * True only if selection was submitted; the server remains authoritative.
     */
    boolean select(Group group, Choice choice);
}
