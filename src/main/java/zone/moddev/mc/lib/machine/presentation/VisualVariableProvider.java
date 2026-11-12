package zone.moddev.mc.lib.machine.presentation;

/**
 * A component's explicit, read-only presentation values. Getters run only on
 * the server thread.
 */
@FunctionalInterface
public interface VisualVariableProvider {
    void defineVisualVariables(VisualVariables.Builder variables);
}
