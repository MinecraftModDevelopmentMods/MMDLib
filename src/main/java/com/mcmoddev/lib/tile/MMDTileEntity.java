package com.mcmoddev.lib.tile;

import java.util.List;
import javax.annotation.Nullable;
import com.google.common.collect.Lists;
import com.mcmoddev.lib.container.IPlayerInventoryProvider;
import com.mcmoddev.lib.container.MMDContainer;
import com.mcmoddev.lib.container.PlayerInventoryInfo;
import com.mcmoddev.lib.gui.IGuiHolder;
import com.mcmoddev.lib.gui.IGuiProvider;
import com.mcmoddev.lib.gui.MMDGuiContainer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class MMDTileEntity extends TileEntity implements IGuiProvider, IPlayerInventoryProvider, IGuiHolder {
    private static final String PARTIAL_SYNC_KEY = "partial_sync";

    //#region IGuiProvider Members

    @Override
    @SideOnly(Side.CLIENT)
    public GuiContainer getClientGui(int id, EntityPlayer player, World world, int x, int y, int z) {
        BlockPos pos = this.getPos();
        if ((world != this.world) || (pos.getX() != x) || (pos.getY() != y) || (pos.getZ() != z)) {
            return null;
        }

        MMDContainer container = this.getServerGui(id, player, world, x, y, z);
        return (container == null) ? null : new MMDGuiContainer(this, player, container);
    }

    @Override
    public MMDContainer getServerGui(int id, EntityPlayer player, World world, int x, int y, int z) {
        BlockPos pos = this.getPos();
        if ((world != this.world) || (pos.getX() != x) || (pos.getY() != y) || (pos.getZ() != z)) {
            return null;
        }

        return new MMDContainer(this, player);
    }

    //#endregion

    @Override
    public List<PlayerInventoryInfo> getPlayerSlots(MMDContainer container) {
        return Lists.newArrayList();
    }

    @Nullable
    public NBTTagCompound getGuiUpdateTag(boolean resetDirtyFlag) {
        return null;
    }

    @Nullable
    public IMessage receiveGuiUpdateTag(NBTTagCompound compound) {
        return null;
    }

    @SuppressWarnings("WeakerAccess")
    @SideOnly(Side.SERVER)
    protected void sendToListeningClients(NBTTagCompound nbt) {
        ChunkPos cp = world.getChunkFromBlockCoords(pos).getPos();
        PlayerChunkMapEntry entry = ((WorldServer)world).getPlayerChunkMap().getEntry(cp.x, cp.z);
        if (entry != null) {
            NBTTagCompound updateTag = new NBTTagCompound();
            updateTag.setTag(PARTIAL_SYNC_KEY, nbt);
            entry.sendPacket(new SPacketUpdateTileEntity(this.getPos(), 42, updateTag));
        }
    }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(this.getPos(), 42, this.getUpdateTag());
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound compound = super.getUpdateTag();
        this.writeToUpdateTag(compound);
        return compound;
    }

    @SuppressWarnings("WeakerAccess")
    protected void writeToUpdateTag(NBTTagCompound tag) {}

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        if (tag.hasKey(PARTIAL_SYNC_KEY, Constants.NBT.TAG_COMPOUND)) {
            this.readFromUpdateTag(tag.getCompoundTag(PARTIAL_SYNC_KEY));
        }
        else {
            super.handleUpdateTag(tag);
        }
    }

    @SuppressWarnings("WeakerAccess")
    protected void readFromUpdateTag(NBTTagCompound tag) {}
}
