package com.mcmoddev.lib.container.widget;

import javax.annotation.Nullable;
import com.mcmoddev.lib.container.gui.GuiContext;

public interface IContextualWidget extends IWidget {
    void setContext(GuiContext context);

    @Nullable
    GuiContext getContext();
}
