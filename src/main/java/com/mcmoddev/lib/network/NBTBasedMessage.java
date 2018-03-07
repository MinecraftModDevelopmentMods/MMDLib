package com.mcmoddev.lib.network;

import com.mcmoddev.lib.util.LoggingUtil;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * Base implementation of an {@link IMessage} that works with NBT Tag Compounds.
 */
@SuppressWarnings("WeakerAccess")
public abstract class NBTBasedMessage implements IMessage {
    private NBTTagCompound compound;

    /**
     * Initializes a new instance of NBTBasedMessage.
     */
    protected NBTBasedMessage() {
    }

    /**
     * Initializes a new instance of NBTBasedMessage.
     * @param compound The tag compound representing the message body.
     */
    protected NBTBasedMessage(final NBTTagCompound compound) {
        this.compound = compound;
    }

    /**
     * Retrieves the tag compound representing the message body.
     * @return The tag compound representing the message body.
     */
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
