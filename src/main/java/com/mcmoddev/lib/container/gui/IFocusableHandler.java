package com.mcmoddev.lib.container.gui;

import javax.annotation.Nullable;

public interface IFocusableHandler {
    @Nullable
    IFocusableWidgetGui getCurrentFocus();

    void setFocus(IFocusableWidgetGui widgetGui);
}
