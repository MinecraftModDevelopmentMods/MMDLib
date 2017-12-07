package com.mcmoddev.lib.gui;

import javax.annotation.Nullable;
import com.mcmoddev.lib.container.IContainerSlotsProvider;
import com.mcmoddev.lib.container.IPlayerInventoryProvider;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public interface IGuiHolder {
    default boolean isValid() { return true; }
    default int getDistance(EntityPlayer player) { return 0; }

    @Nullable
    default IContainerSlotsProvider getSlotsProvider() {
        return (this instanceof IContainerSlotsProvider) ? (IContainerSlotsProvider)this : null;
    }

    @Nullable
    default IGuiPieceProvider getPieceProvider() {
        return (this instanceof IGuiPieceProvider) ? (IGuiPieceProvider)this : null;
    }

    @Nullable
    default IPlayerInventoryProvider getPlayerInventoryProvider() {
        return (this instanceof IPlayerInventoryProvider) ? (IPlayerInventoryProvider)this : null;
    }

    @Nullable
    default IMessage receiveGuiUpdateTag(NBTTagCompound compound) { return null; }

    @Nullable
    default NBTTagCompound getGuiUpdateTag() { return null; }
}
