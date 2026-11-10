package zone.moddev.mc.lib.gui.client;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.gui.ElementAction;
import zone.moddev.mc.lib.gui.ElementActions;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Element;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;

/**
 * Live services for one client behavior; no host getters or raw packet/menu
 * access.
 */
public interface ElementAccess {
    Element element();

    Rect bounds();

    Object value();

    Object maximum();

    ElementRenderAccess presentation();

    /**
     * Optional host-specific behavior services; render access stays read-only.
     */
    default <T> java.util.Optional<T> service(Class<T> type) {
        return java.util.Optional.empty();
    }

    List<Element> elements();

    ResourceLocation screenId();

    boolean pending(Element element);

    <T> boolean submit(Element element, ElementAction<T> action, T value);

    default <T> boolean submit(ElementAction<T> action, T value) {
        return submit(element(), action, value);
    }

    default boolean click(ElementAction<ElementActions.Click> action,
        int button, int option) {
        return submit(action,
            new ElementActions.Click(button, modifiers(), option));
    }

    default int modifiers() {
        return (net.minecraft.client.gui.screens.Screen.hasShiftDown() ? 1 : 0)
            | (net.minecraft.client.gui.screens.Screen.hasControlDown() ? 2 : 0)
            | (net.minecraft.client.gui.screens.Screen.hasAltDown() ? 4 : 0);
    }

    void navigate(String context, String view);

    ItemStack carried();

    SlotPlan reservation(SlotElement element);

    boolean showRecipes();
}
