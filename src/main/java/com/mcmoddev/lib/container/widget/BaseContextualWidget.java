package com.mcmoddev.lib.container.widget;

import javax.annotation.Nullable;
import com.mcmoddev.lib.container.gui.GuiContext;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Base implementation of an {@link IContextualWidget}.
 */
@SuppressWarnings("WeakerAccess")
public class BaseContextualWidget extends BaseWidget implements IContextualWidget {
    private GuiContext context;

    /**
     * Initializes a new instance of BaseContextualWidget.
     * @param key The key that uniquely identified this widget.
     */
    protected BaseContextualWidget(final String key) {
        super(key);
    }

    /**
     * Initializes a new instance of BaseContextualWidget.
     * @param key The key that uniquely identified this widget.
     * @param canBeDirty Specified if this widgets can get dirty or it only contains static data.
     */
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

    /**
     * Gets the player this widget is currently handling data for.
     * @return The player this widget is currently handling data for. Might be null if gui context is not set.
     */
    @Nullable
    protected EntityPlayer getPlayer() {
        final GuiContext context = this.getContext();
        return (context == null) ? null : context.getPlayer();
    }
}
