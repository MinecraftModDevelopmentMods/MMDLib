package com.mcmoddev.lib.container.gui;

/**
 * Marks a {@link IWidgetGui} as able to receive focus.
 */
public interface IFocusableWidgetGui extends IWidgetGui {
    /**
     * Sets the parent focusable handler.
     * @param handler The focusable handler.
     */
    void setFocusableHandler(IFocusableHandler handler);

    /**
     * Called after the widget received focus.
     */
    void onFocus();

    /**
     * Called after the widget lost focus.
     */
    void onBlur();

    /**
     * Sets focus to this widget.
     */
    void focus();

    /**
     * If widget is focused, received key press events.
     * @param typedChar Character on the key.
     * @param keyCode LWJGL Keyboard key code.
     * @return True if the key was handled by this widget. Otherwise false.
     */
    boolean handleKeyPress(char typedChar, int keyCode);
}
