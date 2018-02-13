package com.mcmoddev.lib.container.gui;

public interface IFocusableWidgetGui extends IWidgetGui {
    void setFocusableHandler(IFocusableHandler handler);

    void onFocus();
    void onBlur();

    void focus();

    boolean handleKeyPress(char typedChar, int keyCode);
}
