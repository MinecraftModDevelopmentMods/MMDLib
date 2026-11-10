package zone.moddev.mc.lib.gui.runtime.component;

/**
 * One registered component's state for one open menu. Factories must return
 * fresh instances.
 */
public interface MenuComponent {
    /**
     * Stable per-menu presentation trees outside the screen model, in matching
     * client/server order.
     */
    default java.util.List<? extends zone.moddev.mc.lib.gui.model.Element> presentationRoots() {
        return java.util.List.of();
    }

    default void tickClient() {
    }

    default void beforeBroadcast() {
    }

    default void broadcast() {
    }

    default void beforeInteraction() {
    }

    default void afterInteraction() {
    }

    default void removed() {
    }

    default boolean pendingInventory() {
        return false;
    }
}
