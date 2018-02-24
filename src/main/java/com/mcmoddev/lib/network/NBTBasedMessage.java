package com.mcmoddev.lib.network;

import com.mcmoddev.lib.util.LoggingUtil;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public abstract class NBTBasedMessage implements IMessage {
    private NBTTagCompound compound;

    public NBTBasedMessage() {
    }

    public NBTBasedMessage(final NBTTagCompound compound) {
        this.compound = compound;
    }

    public NBTTagCompound getCompound() {
        return this.compound;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        this.compound = ByteBufUtils.readTag(buf);

        LoggingUtil.logNbtMessage("FROM BYTE BUF", this.compound);
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        ByteBufUtils.writeTag(buf, this.compound);

        LoggingUtil.logNbtMessage("TO BYTE BUF", this.compound);
    }
}
