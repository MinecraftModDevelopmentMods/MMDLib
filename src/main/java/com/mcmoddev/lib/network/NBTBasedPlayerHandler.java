package com.mcmoddev.lib.network;

import javax.annotation.Nullable;
import com.mcmoddev.lib.MMDLib;
import com.mcmoddev.lib.gui.MMDContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

public class NBTBasedPlayerHandler implements IMessageHandler<NBTBasedPlayerMessage, IMessage> {
    public static final NBTBasedPlayerHandler INSTANCE = new NBTBasedPlayerHandler();
    private NBTBasedPlayerHandler() {}

    @Nullable
    @Override
    public IMessage onMessage(NBTBasedPlayerMessage message, MessageContext ctx) {
        if (ctx.side == Side.CLIENT) {
            // process message from server
            EntityPlayerSP player = Minecraft.getMinecraft().player;
            String playerId = player.getUniqueID().toString();
            if (playerId.equals(message.getPlayerId())) {
                if (player.openContainer instanceof MMDContainer) {
                    // TODO: find a way to also support this for non-tile-based containers
                    // like, use the same interface we use for tile entities
                    return ((MMDContainer)player.openContainer).handleMessageFromServer(message.getCompound());
                }
                else {
                    MMDLib.logger.error("Message received for player. But wrong type of container is currently opened!");
                }
            }
            else {
                MMDLib.logger.error("Message received for wrong player: '" + message.getPlayerId() + "'");
            }
        }
        else if (ctx.side == Side.SERVER) {
            // process message from client
            // TODO: handle this
            return null;
        }
        return null;
    }
}
