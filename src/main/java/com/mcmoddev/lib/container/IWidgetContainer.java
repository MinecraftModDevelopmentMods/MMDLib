package com.mcmoddev.lib.container;

import java.util.List;
import javax.annotation.Nullable;
import com.google.common.collect.Lists;
import com.mcmoddev.lib.container.gui.GuiContext;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.gui.IWidgetLayout;
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
    default int getDistance(EntityPlayer player) { return 0; }

    /**
     * Returns the list of widgets used to build a {@link MMDContainer}.
     * @return The list of widgets used to build a {@link MMDContainer}.
     */
    default List<IWidget> getWidgets(GuiContext context) { return Lists.newArrayList(); }

    /**
     * Returns the combined update tag from all the contained widgets.
     * @param resetDirtyFlag Specified if a call to {@link IWidget#resetDirtyFlag()} will also be made for all dirty widgets.
     * @return The combined NBT from all the container widgets.
     * @implNote This will only get called on server thread.
     */
    @Nullable
    default NBTTagCompound getGuiUpdateTag(boolean resetDirtyFlag) { return null; }

    /**
     * Returns the root Widget Gui used to build this container's gui. Usually this will be a {@link IWidgetLayout}.
     * @return The root Widget Gui of this container.
     * @implNote This will only get called on client thread.
     */
    // TODO: return an empty canvas by default
    @SideOnly(Side.CLIENT)
    default IWidgetGui getRootWidgetGui(GuiContext context) { return null; }

    @Nullable
    @SideOnly(Side.CLIENT)
    default IMessage receiveGuiUpdateTag(NBTTagCompound compound) { return null; }
}
