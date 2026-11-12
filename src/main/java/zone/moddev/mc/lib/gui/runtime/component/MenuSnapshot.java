package zone.moddev.mc.lib.gui.runtime.component;

/**
 * Typed component snapshots; every field must be declared in the component
 * registration.
 */
public interface MenuSnapshot extends MenuComponent {
    /**
     * Samples authoritative server state; omitted fields retain their previous
     * value.
     */
    void snapshot(ComponentSnapshot values);

    /**
     * Called once after changed fields commit, during server sampling or client
     * updates. Initial values do not trigger this callback. Reads through the
     * view return independent decoded values.
     */
    default void applied(ComponentStateView values) {
    }
}
