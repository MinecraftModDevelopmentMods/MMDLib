package com.mcmoddev.lib.container.widget;

import javax.annotation.Nullable;
import com.mcmoddev.lib.container.gui.GuiContext;

/**
 * Represents a widget that needs access to the gui context it lives in.
 */
public interface IContextualWidget extends IWidget {
    /**
     * Sets the current gui context.
     * @param context The gui context.
     */
    void setContext(GuiContext context);

    /**
     * Gets the current gui context for this widget.
     * @return The current gui context. Can be null.
     */
    @Nullable
    GuiContext getContext();
}
