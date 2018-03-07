package com.mcmoddev.lib.container.widget;

/**
 * Represents a widget that was created as a specific widget for a gui context.
 */
public interface IProxiedWidget extends IWidget {
    /**
     * Gets the original widget used to create this one.
     * @return The original widget.
     */
    IWidget getOriginalWidget();
}
