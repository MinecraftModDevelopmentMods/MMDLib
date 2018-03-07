package com.mcmoddev.lib.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;

/**
 * A message type that contains a player reference and a nbt tag compound.
 */
public class NBTBasedPlayerMessage extends NBTBasedMessage {
    private String playerId;

    /**
     * Creates a new instance of NBTBasedPlayerMessage.
     */
    public NBTBasedPlayerMessage() {
        super();
    }

    /**
     * Creates a new instance of NBTBasedPlayerMessage.
     * @param player The player this message is for.
     * @param compound The message body.
     */
    public NBTBasedPlayerMessage(final EntityPlayer player, final NBTTagCompound compound) {
        super(compound);
        this.playerId = player.getGameProfile().getId().toString();
    }

    /**
     * Returns the id of the player this message is for.
     * @return The id of the player this message is for.
     */
    public String getPlayerId() {
        return this.playerId;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        this.playerId = ByteBufUtils.readUTF8String(buf);
        super.fromBytes(buf);
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, ((this.playerId == null) || (this.playerId.length() == 0)) ? "" : this.playerId);
        super.toBytes(buf);
    }

    /**
     * Helper method to send a NBTBasedPlayerMessage to server side.
     * @param player The player this message should be sent to.
     * @param data The tag compound representing the message body.
     */
    public static void sendToServer(final EntityPlayerSP player, final NBTTagCompound data) {
        MMDMessages.sendToServer(new NBTBasedPlayerMessage(player, data));
    }
}
