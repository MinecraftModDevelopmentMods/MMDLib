package com.mcmoddev.lib.container;

import java.util.List;
import javax.annotation.Nullable;
import com.google.common.collect.Lists;
import com.mcmoddev.lib.container.gui.GuiContext;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.gui.IWidgetLayout;
import com.mcmoddev.lib.container.gui.layout.CanvasLayout;
import com.mcmoddev.lib.container.widget.IWidget;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Represents a common provider for Widgets and Widget Guis.
 */
public interface IWidgetContainer {
    default boolean isValid() { return true; }
    default int getDistance(final EntityPlayer player) { return 0; }

    /**
     * Returns the list of widgets used to build a {@link MMDContainer}.
     * @return The list of widgets used to build a {@link MMDContainer}.
     */
    default List<IWidget> getWidgets(final GuiContext context) { return Lists.newArrayList(); }

    /**
     * Returns the root Widget Gui used to build this container's gui. Usually this will be a {@link IWidgetLayout}.
     * @return The root Widget Gui of this container.
     * @implNote This will only get called on client thread.
     */
    @SideOnly(Side.CLIENT)
    default IWidgetGui getRootWidgetGui(final GuiContext context) { return new CanvasLayout(); }

    @Nullable
    default NBTTagCompound getGuiUpdateTag(final boolean resetDirtyFlag) { return null; }

    @Nullable
    @SideOnly(Side.CLIENT)
    default IMessage receiveGuiUpdateTag(final NBTTagCompound compound) { return null; }
}
