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
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Represents a common provider for {@link IWidget Widgets} and {@link IWidgetGui Widget GUIs}.
 */
public interface IWidgetContainer {
    /**
     * Returns a value indicating if this container is valid or not. An invalid container should not display a GUI.
     * @return True if the container is valid. False if it is not.
     */
    default boolean isValid() { return true; }

    /**
     * Gets the distance between this container and a player. Only makes sense when this interface is implemented by a Tile Entity.
     * @param player The player to compute distance to.
     * @return The distance from the player to this container.
     */
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

    /**
     * Gets a nbt compound to be sent to client. Called every tick.
     * @param resetDirtyFlag True is it should reset dirty flags on all children.
     * @return The nbt compound to be sent to client. Or null if no update is needed.
     */
    @Nullable
    default NBTTagCompound getGuiUpdateTag(final boolean resetDirtyFlag) { return null; }

    /**
     * Receives a tag compound containing update information from server.
     * @param compound The tag compound containing the update information.
     */
    @SideOnly(Side.CLIENT)
    default void receiveGuiUpdateTag(final NBTTagCompound compound) { }
}
