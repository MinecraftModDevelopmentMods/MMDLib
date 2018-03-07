package com.mcmoddev.lib.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Used to mark a receiver of NBT based messages.
 */
public interface INBTMessageReceiver {
    /**
     * Called to handle a message received from server side.
     * @param compound The message content.
     */
    void receiveFromServer(NBTTagCompound compound);

    /**
     * Called to handle a message received from client side.
     * @param player The player this message originated from.
     * @param compound The message content.
     */
    void receiveFromClient(EntityPlayerMP player, NBTTagCompound compound);
}
