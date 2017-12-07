package com.mcmoddev.lib.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public abstract class NBTBasedMessage implements IMessage {
    private NBTTagCompound compound;

    public NBTBasedMessage() {
    }

    public NBTBasedMessage(NBTTagCompound compound) {
        this.compound = compound;
    }

    public NBTTagCompound getCompound() {
        return this.compound;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.compound = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, this.compound);
    }
}
