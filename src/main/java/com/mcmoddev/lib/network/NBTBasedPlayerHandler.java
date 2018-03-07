package com.mcmoddev.lib.network;

import javax.annotation.Nullable;
import com.mcmoddev.lib.MMDLib;
import com.mcmoddev.lib.container.MMDContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/**
 * {@link IMessageHandler} for {@link NBTBasedPlayerMessage}.
 */
public class NBTBasedPlayerHandler implements IMessageHandler<NBTBasedPlayerMessage, IMessage> {
    public static final NBTBasedPlayerHandler INSTANCE = new NBTBasedPlayerHandler();
    private NBTBasedPlayerHandler() {}

    @Nullable
    @Override
    public IMessage onMessage(final NBTBasedPlayerMessage message, final MessageContext ctx) {
        if (ctx.side == Side.CLIENT) {
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
                // process message from server
                final EntityPlayerSP player = Minecraft.getMinecraft().player;
                final String playerId = player.getUniqueID().toString();
                if (playerId.equals(message.getPlayerId())) {
                    if (player.openContainer instanceof MMDContainer) {
                        ((MMDContainer) player.openContainer).handleMessageFromServer(message.getCompound());
                    } else {
                        MMDLib.logger.error("Message received for player. But wrong type of container is currently opened!");
                    }
                } else {
                    MMDLib.logger.error("Message received for wrong player: '" + message.getPlayerId() + "'");
                }
            });
        } else if (ctx.side == Side.SERVER) {
            // process message from client
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
                final EntityPlayerMP player = ctx.getServerHandler().player;
                final String playerId = player.getUniqueID().toString();
                if (playerId.equals(message.getPlayerId())) {
                    if (player.openContainer instanceof MMDContainer) {
                        // TODO: find a way to also support this for non-tile-based containers
                        // like, use the same interface we use for tile entities
                        ((MMDContainer) player.openContainer).handleMessageFromClient(message.getCompound());
                    } else {
                        MMDLib.logger.error("Message received for player. But wrong type of container is currently opened!");
                    }
                } else {
                    MMDLib.logger.error("Message received for wrong player: '" + message.getPlayerId() + "'");
                }
            });
        }

        return null;
    }
}
