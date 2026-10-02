package zone.moddev.mc.mmdlib.container;

/**
 * Used by things that don't/can't implement IWidgetContainer themselves.
 */
public interface IWidgetContainerProxy {
    /**
     * Gets the real widget container.
     * @return The real widget container.
     */
    IWidgetContainer getWidgetContainer();
}
