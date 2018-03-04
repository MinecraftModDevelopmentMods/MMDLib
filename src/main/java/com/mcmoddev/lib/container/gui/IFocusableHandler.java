package com.mcmoddev.lib.container.gui;

import javax.annotation.Nullable;

/**
 * Implemented by {@link MMDGuiContainer}. Used to provide context to focusable widget guis.
 */
public interface IFocusableHandler {
    /**
     * Returns the focusable widget gui that currently has focus.
     * @return The focusable widget gui that currently has focus.
     */
    @Nullable
    IFocusableWidgetGui getCurrentFocus();

    /**
     * Sets the focusable widget gui that currently has focus.
     * @param widgetGui The focusable widget gui that should become the currently focused one.
     */
    void setFocus(@Nullable IFocusableWidgetGui widgetGui);
}
