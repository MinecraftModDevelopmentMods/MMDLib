package com.mcmoddev.lib.network;

import javax.annotation.Nullable;

import com.mcmoddev.lib.MMDLib;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class NBTBasedTileHandlerClient implements IMessageHandler<NBTBasedTileMessage, IMessage> {
	public static final NBTBasedTileHandlerClient INSTANCE = new NBTBasedTileHandlerClient();
	
	public NBTBasedTileHandlerClient() {}

    @Nullable
    @Override
	public IMessage onMessage(NBTBasedTileMessage message, MessageContext ctx) {
        FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
            final int dimensionId = message.getDimensionId();
            final World world = net.minecraft.client.Minecraft.getMinecraft().world;
            if (world.provider.getDimension() == dimensionId) {
                final BlockPos pos = new BlockPos(message.getPosX(), message.getPosY(), message.getPosZ());
                if (world.isBlockLoaded(pos)) {
                    // 1. test tile entity
                    final TileEntity tile = world.getTileEntity(pos);
                    if ((tile != null) && (tile instanceof INBTMessageReceiver)) {
                        ((INBTMessageReceiver) tile).receiveFromServer(message.getCompound());
                    }

                    // 2. test block (do we even care about blocks?)
                    final Block block = world.getBlockState(pos).getBlock();
                    if (block instanceof INBTMessageReceiver) {
                        ((INBTMessageReceiver) block).receiveFromServer(message.getCompound());
                    }
                } else {
                    MMDLib.logger.error("Message received for a block position that is not loaded..");
                }
            } else {
                MMDLib.logger.error("Message received for wrong dimension.");
            }
        });
        
        return null;
	}

}
