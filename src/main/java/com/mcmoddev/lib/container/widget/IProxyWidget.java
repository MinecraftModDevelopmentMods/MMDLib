package com.mcmoddev.lib.container.widget;

import com.mcmoddev.lib.container.gui.GuiContext;

public interface IProxyWidget extends IWidget {
    IWidget getContextualWidget(GuiContext context);
}
