package com.mcmoddev.lib.container.widget;

import javax.annotation.Nullable;
import com.mcmoddev.lib.container.gui.GuiContext;

public class BaseContextualWidget extends BaseWidget implements IContextualWidget {
    private GuiContext context;

    protected BaseContextualWidget(String key) {
        super(key);
    }

    protected BaseContextualWidget(String key, boolean canBeDirty) {
        super(key, canBeDirty);
    }

    @Override
    public void setContext(GuiContext context) {
        this.context = context;
    }

    @Nullable
    @Override
    public GuiContext getContext() {
        return this.context;
    }
}
