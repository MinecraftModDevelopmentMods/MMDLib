package com.mcmoddev.lib.network;

import com.mcmoddev.lib.MMDLib;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public final class MMDPackages {
    private MMDPackages() {}
    private static SimpleNetworkWrapper wrapper = new SimpleNetworkWrapper(MMDLib.MODID);

    public static void init() {
        MMDPackages.wrapper.registerMessage(NBTBasedPlayerHandler.INSTANCE, NBTBasedPlayerMessage.class, 1, Side.CLIENT);
        MMDPackages.wrapper.registerMessage(NBTBasedPlayerHandler.INSTANCE, NBTBasedPlayerMessage.class, 1, Side.SERVER);
    }

    @SideOnly(Side.SERVER)
    public static void sendToPlayer(EntityPlayerMP player, IMessage message) {
        MMDPackages.wrapper.sendTo(message, player);
    }

    @SideOnly(Side.SERVER)
    public static void sendToServer(IMessage message) {
        MMDPackages.wrapper.sendToServer(message);
    }
}
