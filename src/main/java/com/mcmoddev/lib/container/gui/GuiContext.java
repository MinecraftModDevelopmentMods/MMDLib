package com.mcmoddev.lib.container.gui;

import java.util.List;
import javax.annotation.Nullable;
import com.mcmoddev.lib.container.IWidgetContainer;
import com.mcmoddev.lib.container.MMDContainer;
import com.mcmoddev.lib.container.widget.IWidget;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class GuiContext {
    private final EntityPlayer player;
    private final MMDContainer container;
    private final IWidgetContainer provider;

    @SideOnly(Side.CLIENT)
    private MMDGuiContainer guiContainer = null; // too lazy to find a way to make this final

    public GuiContext(EntityPlayer player, MMDContainer container, IWidgetContainer holder) {
        this.player = player;
        this.container = container;
        this.provider = holder;
    }

    @SideOnly(Side.CLIENT)
    public GuiContext(EntityPlayer player, MMDContainer container, MMDGuiContainer guiContainer, IWidgetContainer holder) {
        this(player, container, holder);
        this.guiContainer = guiContainer;
    }

    public EntityPlayer getPlayer() {
        return this.player;
    }

    public MMDContainer getContainer() {
        return this.container;
    }

    @Nullable
    @SideOnly(Side.CLIENT)
    public MMDGuiContainer getGuiContainer() {
        return this.guiContainer;
    }

    public IWidgetContainer getWidgetProvider() {
        return this.provider;
    }

    public List<IWidget> getWidgets() {
        return this.container.getWidgets();
    }

    @Nullable
    public IWidget findWidgetByKey(String key) {
        return this.container.findWidgetByKey(key);
    }
}
