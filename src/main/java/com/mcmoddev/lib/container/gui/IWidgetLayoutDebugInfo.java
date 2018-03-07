package com.mcmoddev.lib.container.gui;

/**
 * Interface used to print debug info of a widget layout.
 */
public interface IWidgetLayoutDebugInfo extends IWidgetLayout {
    /**
     * Gets debug information about a child widget.
     * @param child Child widget to get information for.
     * @return The debug information for the specified child.
     */
    String getDebugInfo(IWidgetGui child);
}
