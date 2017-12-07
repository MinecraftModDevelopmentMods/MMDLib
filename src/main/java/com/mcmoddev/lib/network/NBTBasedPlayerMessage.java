package com.mcmoddev.lib.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;

public class NBTBasedPlayerMessage extends NBTBasedMessage {
    private String playerId;

    public NBTBasedPlayerMessage() {
        super();
    }

    public NBTBasedPlayerMessage(EntityPlayer player, NBTTagCompound compound) {
        super(compound);
        this.playerId = player.getGameProfile().getId().toString();
    }

    public String getPlayerId() {
        return this.playerId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.playerId = ByteBufUtils.readUTF8String(buf);
        super.fromBytes(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, ((this.playerId == null) || (this.playerId.length() == 0)) ? "" : this.playerId);
        super.toBytes(buf);
    }
}
