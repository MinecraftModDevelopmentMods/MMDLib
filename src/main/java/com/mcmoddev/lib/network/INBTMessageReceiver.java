package com.mcmoddev.lib.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public interface INBTMessageReceiver {
    IMessage receiveFromServer(NBTTagCompound compound);
    IMessage receiveFromClient(EntityPlayerMP player, NBTTagCompound compound);
}
