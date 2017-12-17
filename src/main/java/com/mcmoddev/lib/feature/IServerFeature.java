package com.mcmoddev.lib.feature;

import javax.annotation.Nullable;
import net.minecraft.nbt.NBTTagCompound;

public interface IServerFeature {
    boolean isDirty();

    /**
     * Gets the 'as small as possible' update tag to be send from server to client.
     * This method will only be called on server side.
     * @param resetDirtyFlag Specifies if the 'dirty' flag should be reset.
     * @return The nbt tag to be sent to client.
     */
    @Nullable
    NBTTagCompound getUpdateTag(boolean resetDirtyFlag);
}
