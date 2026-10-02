package zone.moddev.mc.mmdlib.container.gui;

/**
 * Interface used for printing a GUI Tree to the debug logger.
 */
public interface IWidgetGuiDebugInfo extends IWidgetGui {
    /**
     * Returns the debug info for this widget.
     * @return The debug info for this widget.
     */
    String getDebugInfo();
}
