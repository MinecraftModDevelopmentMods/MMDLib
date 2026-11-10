package zone.moddev.mc.lib.gui.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.gui.ElementActions;
import zone.moddev.mc.lib.gui.model.Navigation;
import zone.moddev.mc.lib.gui.model.containers.NavigableElement;

/**
 * Shared prediction and per-screen remembered selections, without concrete
 * control types.
 */
public final class NavigationMemory {
    private static final Map<ResourceLocation, Map<String, String>> REMEMBERED = new HashMap<>();

    private NavigationMemory() {
    }

    public static void navigate(ElementAccess access, String context,
        String selection) {
        access.navigate(context, selection);
        REMEMBERED
            .computeIfAbsent(access.screenId(), ignored -> new HashMap<>())
            .put(context, selection);
    }

    /**
     * Called after authoritative state has been applied, including rejections
     * and timeouts.
     */
    public static void remember(ElementAccess access,
        NavigableElement destination) {
        destination.common().id()
            .ifPresent(id -> REMEMBERED
                .computeIfAbsent(access.screenId(), ignored -> new HashMap<>())
                .put(id, destination.selected(access.presentation().state())));
    }

    public static void restore(ElementAccess access,
        NavigableElement destination) {
        var id = destination.common().id().orElse(null);
        if (id == null)
            return;
        var selected = REMEMBERED.getOrDefault(access.screenId(), Map.of())
            .get(id);
        if (selected == null || selected.equals(destination.defaultSelection())
            || selected
                .equals(destination.selected(access.presentation().state()))
            || !destination.hasSelection(selected))
            return;
        for (var element : access.elements()) {
            var binding = element.navigation().orElse(null);
            if (binding != null && binding.target().context().equals(id)
                && binding.target().view().equals(selected)
                && access.presentation().visible(element)
                && element.action(Navigation.NAVIGATE.id())
                    .orElse(null) == Navigation.NAVIGATE
                && access.submit(element, Navigation.NAVIGATE,
                    new ElementActions.Click(0, 0, 0)))
                // Restore only through an authorized control. Do not predict on
                // another behavior's
                // request.
                return;
        }
    }
}
