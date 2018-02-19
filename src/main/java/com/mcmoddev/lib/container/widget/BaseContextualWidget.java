package com.mcmoddev.lib.container.widget;

import javax.annotation.Nullable;
import com.mcmoddev.lib.container.gui.GuiContext;
import net.minecraft.entity.player.EntityPlayer;

public class BaseContextualWidget extends BaseWidget implements IContextualWidget {
    private GuiContext context;

    protected BaseContextualWidget(final String key) {
        super(key);
    }

    protected BaseContextualWidget(final String key, final boolean canBeDirty) {
        super(key, canBeDirty);
    }

    @Override
    public void setContext(final GuiContext context) {
        this.context = context;
    }

    @Nullable
    @Override
    public GuiContext getContext() {
        return this.context;
    }

    @Nullable
    protected EntityPlayer getPlayer() {
        final GuiContext context = this.getContext();
        return (context == null) ? null : context.getPlayer();
    }
}
