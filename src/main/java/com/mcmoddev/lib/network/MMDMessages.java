package com.mcmoddev.lib.network;

import com.mcmoddev.lib.MMDLib;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Helper class to help with sending/receiving message to/from server.
 */
public final class MMDMessages {
    private MMDMessages() {}
    private final static SimpleNetworkWrapper wrapper = new SimpleNetworkWrapper(MMDLib.MODID);
    
    /**
     * Registers the message types handled by this class.
     */
    public static void client_init() {
        MMDMessages.wrapper.registerMessage(NBTBasedPlayerHandler.INSTANCE, NBTBasedPlayerMessage.class, 1, Side.CLIENT);

        MMDMessages.wrapper.registerMessage(NBTBasedTileHandlerClient.INSTANCE, NBTBasedTileMessage.class, 2, Side.CLIENT);
    }

    public static void server_init() {
        MMDMessages.wrapper.registerMessage(NBTBasedPlayerHandler.INSTANCE, NBTBasedPlayerMessage.class, 1, Side.SERVER);

        MMDMessages.wrapper.registerMessage(NBTBasedTileHandlerServer.INSTANCE, NBTBasedTileMessage.class, 2, Side.SERVER);
    }

    /**
     * Sends a message to a specific player. This method is usually called on server side.
     * @param player Player to send the message to.
     * @param message Message to send.
     */
    public static void sendToPlayer(final EntityPlayerMP player, final IMessage message) {
        MMDMessages.wrapper.sendTo(message, player);
    }

    /**
     * Sends a message to server. This method is usually called on client side.
     * @param message Message to send.
     */
    public static void sendToServer(final IMessage message) {
        MMDMessages.wrapper.sendToServer(message);
    }
}
