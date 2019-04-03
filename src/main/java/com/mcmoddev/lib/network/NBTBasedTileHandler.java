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
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/**
 * {@link IMessageHandler} for {@link NBTBasedTileMessage}.
 */
public class NBTBasedTileHandler implements IMessageHandler<NBTBasedTileMessage, IMessage> {
    public static final NBTBasedTileHandler INSTANCE = new NBTBasedTileHandler();
    private NBTBasedTileHandler() {}

    @Nullable
    @Override
    public IMessage onMessage(final NBTBasedTileMessage message, final MessageContext ctx) {
        if (ctx.side == Side.CLIENT) {
            // process message from server
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
                final int dimensionId = message.getDimensionId();
                final World world = Minecraft.getMinecraft().world;
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
        }
        else if (ctx.side == Side.SERVER) {
            // process message from client
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(() -> {
                final int dimensionId = message.getDimensionId();
                final World world = DimensionManager.getWorld(dimensionId);
                if (world != null) {
                    final BlockPos pos = new BlockPos(message.getPosX(), message.getPosY(), message.getPosZ());
                    if (world.isBlockLoaded(pos)) {
                        final EntityPlayerMP sender = ctx.getServerHandler().player;
                        // 1. test tile entity
                        final TileEntity tile = world.getTileEntity(pos);
                        if ((tile != null) && (tile instanceof INBTMessageReceiver)) {
                            ((INBTMessageReceiver) tile).receiveFromClient(sender, message.getCompound());
                        }

                        // 2. test block (do we even care about blocks?)
                        final Block block = world.getBlockState(pos).getBlock();
                        if (block instanceof INBTMessageReceiver) {
                            ((INBTMessageReceiver) block).receiveFromClient(sender, message.getCompound());
                        }
                    } else {
                        MMDLib.logger.error("Message received for a block position that is not loaded..");
                    }
                } else {
                    MMDLib.logger.error("Message received for an unknown dimension.");
                }
            });
        }
        return null;
    }
}
