package com.mcmoddev.lib.feature;

import javax.annotation.Nullable;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;

public interface IServerFeature extends IFeature {
    boolean isDirty();

    /**
     * Gets the 'as small as possible' update tag to be send from server to client.
     * This method is called from the {@link Container#detectAndSendChanges()}
     * This method will only be called on server side.
     * @param resetDirtyFlag Specifies if the 'dirty' flag should be reset.
     * @return The nbt tag to be sent to client. Return 'null' if no tag should be sent. Empty tags will be sent.
     */
    @Nullable
    NBTTagCompound getGuiUpdateTag(boolean resetDirtyFlag);

    /**
     * Gets the 'as small as possible' update tag to be send from server to client.
     * This method is called only by tile entities. This method is called every tick for dirty entities.
     * This method will only be called on server side.
     * @param resetDirtyFlag Specifies if the 'dirty' flag should be reset.
     * @return The nbt tag to be sent to client. Return 'null' if no tag should be sent. Empty tags will be sent.
     */
    @Nullable
    NBTTagCompound getTickUpdateTag(boolean resetDirtyFlag);

    /**
     * Gets the 'as small as possible' update tag to be send from server to client.
     * This method is called only by tile entities after they get loaded into the world.
     * This method will only be called on server side.
     * @return The nbt tag to be sent to client. Return 'null' if no tag should be sent. Empty tags will be sent.
     */
    @Nullable
    NBTTagCompound getLoadUpdateTag();
}
