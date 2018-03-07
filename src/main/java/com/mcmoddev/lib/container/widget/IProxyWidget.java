package com.mcmoddev.lib.container.widget;

import com.mcmoddev.lib.container.gui.GuiContext;

/**
 * Represents a generic widget that will provide a specific widget for each gui context.
 *
 * Usually used by widgets that also exist outside containers but need to provide per-container dirty flag handling.
 */
public interface IProxyWidget extends IWidget {
    /**
     * Gets the specific widget for the specified gui context.
     * @param context The context to get the specific widget for.
     * @return The specific widget linked to the provided context.
     */
    IWidget getContextualWidget(GuiContext context);
}
