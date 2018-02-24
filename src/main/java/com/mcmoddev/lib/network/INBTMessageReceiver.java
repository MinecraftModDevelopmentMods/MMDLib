package com.mcmoddev.lib.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

public interface INBTMessageReceiver {
    void receiveFromServer(NBTTagCompound compound);
    void receiveFromClient(EntityPlayerMP player, NBTTagCompound compound);
}
