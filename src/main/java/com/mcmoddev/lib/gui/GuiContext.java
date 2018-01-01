package com.mcmoddev.lib.gui;

import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;

public class GuiContext {
    private final EntityPlayer player;
    private final MMDContainer container;
    private final MMDGuiContainer guiContainer;
    private final IGuiHolder holder;

    GuiContext(EntityPlayer player, MMDContainer container, @Nullable MMDGuiContainer guiContainer, IGuiHolder holder) {
        this.player = player;
        this.container = container;

        this.guiContainer = guiContainer;
        this.holder = holder;
    }

    public EntityPlayer getPlayer() {
        return player;
    }

    public MMDContainer getContainer() {
        return container;
    }

    @Nullable
    public MMDGuiContainer getGuiContainer() {
        return guiContainer;
    }

    public IGuiHolder getGuiHolder() {
        return holder;
    }
}
