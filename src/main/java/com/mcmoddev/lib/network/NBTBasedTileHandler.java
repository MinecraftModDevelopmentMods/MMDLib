package com.mcmoddev.lib.network;

import javax.annotation.Nullable;
import com.mcmoddev.lib.MMDLib;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

public class NBTBasedTileHandler implements IMessageHandler<NBTBasedTileMessage, IMessage> {
    @Nullable
    @Override
    public IMessage onMessage(NBTBasedTileMessage message, MessageContext ctx) {
        if (ctx.side == Side.CLIENT) {
            // process message from server
            int dimensionId = message.getDimensionId();
            World world = Minecraft.getMinecraft().world;
            if (world.provider.getDimension() == dimensionId) {
                BlockPos pos = new BlockPos(message.getPosX(), message.getPosY(), message.getPosZ());
                if (world.isBlockLoaded(pos)) {
                    // 1. test tile entity
                    TileEntity tile = world.getTileEntity(pos);
                    if ((tile != null) && (tile instanceof INBTMessageReceiver)) {
                        return ((INBTMessageReceiver)tile).receiveFromServer(message.getCompound());
                    }

                    // 2. test block (do we even care about blocks?)
                    Block block = world.getBlockState(pos).getBlock();
                    if (block instanceof INBTMessageReceiver) {
                        return ((INBTMessageReceiver)block).receiveFromServer(message.getCompound());
                    }
                }
                else {
                    MMDLib.logger.error("Message received for a block position that is not loaded..");
                }
            }
            else {
                MMDLib.logger.error("Message received for wrong dimension.");
            }
        }
        else if (ctx.side == Side.SERVER) {
            // process message from client
            int dimensionId = message.getDimensionId();
            World world = DimensionManager.getWorld(message.getDimensionId());
            if (world != null) {
                BlockPos pos = new BlockPos(message.getPosX(), message.getPosY(), message.getPosZ());
                if (world.isBlockLoaded(pos)) {
                    EntityPlayerMP sender = ctx.getServerHandler().player;
                    // 1. test tile entity
                    TileEntity tile = world.getTileEntity(pos);
                    if ((tile != null) && (tile instanceof INBTMessageReceiver)) {
                        return ((INBTMessageReceiver)tile).receiveFromClient(sender, message.getCompound());
                    }

                    // 2. test block (do we even care about blocks?)
                    Block block = world.getBlockState(pos).getBlock();
                    if (block instanceof INBTMessageReceiver) {
                        return ((INBTMessageReceiver)block).receiveFromClient(sender, message.getCompound());
                    }
                }
                else {
                    MMDLib.logger.error("Message received for a block position that is not loaded..");
                }
            }
            else {
                MMDLib.logger.error("Message received for an unknown dimension.");
            }
        }
        return null;
    }
}
