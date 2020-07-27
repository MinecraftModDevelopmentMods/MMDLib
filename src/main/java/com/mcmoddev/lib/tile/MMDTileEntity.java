package com.mcmoddev.lib.tile;

import javax.annotation.Nullable;
import com.mcmoddev.lib.container.IGuiProvider;
import com.mcmoddev.lib.container.IWidgetContainer;
import com.mcmoddev.lib.container.MMDContainer;
import com.mcmoddev.lib.container.gui.MMDGuiContainer;
import com.mcmoddev.lib.util.LoggingUtil;
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
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class MMDTileEntity extends TileEntity implements IGuiProvider, IWidgetContainer {
    private static final String PARTIAL_SYNC_KEY = "partial_sync";

    //#region IGuiProvider Members

    @Override
    @SideOnly(Side.CLIENT)
    public GuiContainer getClientGui(final int id, final EntityPlayer player, final World world, final int x, final int y, final int z) {
        final BlockPos pos = this.getPos();
        if ((world != this.world) || (pos.getX() != x) || (pos.getY() != y) || (pos.getZ() != z)) {
            return null;
        }

        final MMDContainer container = this.getServerGui(id, player, world, x, y, z);
        return (container == null) ? null : new MMDGuiContainer(this, player, container);
    }

    @Override
    public MMDContainer getServerGui(final int id, final EntityPlayer player, final World world, final int x, final int y, final int z) {
        final BlockPos pos = this.getPos();
        if ((world != this.world) || (pos.getX() != x) || (pos.getY() != y) || (pos.getZ() != z)) {
            return null;
        }

        return new MMDContainer(this, player);
    }

    //#endregion

    @Nullable
    public NBTTagCompound getGuiUpdateTag(final boolean resetDirtyFlag) {
        return null;
    }

    public void receiveGuiUpdateTag(final NBTTagCompound compound) {
        this.readFromUpdateTag(compound);
    }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        final NBTTagCompound tag = this.getUpdateTag();
        if (!tag.isEmpty()) {
            //LoggingUtil.logNbtMessage(this, "SPacket TAG", tag);
            return new SPacketUpdateTileEntity(this.getPos(), 42, tag);
        }
        return null;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        final NBTTagCompound compound = super.getUpdateTag();
        this.writeToUpdateTag(compound);

        if (!compound.isEmpty()) {
            //LoggingUtil.logNbtMessage(this, "GET UPDATE NBT", compound);
        }
        return compound;
    }

    protected void writeToUpdateTag(final NBTTagCompound tag) {
        // just send the entire entity by default
        this.writeToNBT(tag);
    }

    @Override
    public void handleUpdateTag(final NBTTagCompound tag) {
        //LoggingUtil.logNbtMessage(this, "HANDLE UPDATE NBT", tag);

        if (tag.hasKey(PARTIAL_SYNC_KEY, Constants.NBT.TAG_COMPOUND)) {
            this.readFromUpdateTag(tag.getCompoundTag(PARTIAL_SYNC_KEY));
        }
        else {
            this.readFromUpdateTag(tag);
        }
    }

    protected void readFromUpdateTag(final NBTTagCompound tag) {
        this.readFromNBT(tag);
    }

    protected void sendToListeningClients(final NBTTagCompound nbt) {
        final ChunkPos cp = world.getChunk(pos).getPos();
        final PlayerChunkMapEntry entry = ((WorldServer)world).getPlayerChunkMap().getEntry(cp.x, cp.z);
        if (entry != null) {
            final NBTTagCompound updateTag = new NBTTagCompound();
            updateTag.setTag(PARTIAL_SYNC_KEY, nbt);
            //LoggingUtil.logNbtMessage(this, "SEND TO CLIENT", nbt);
            entry.sendPacket(new SPacketUpdateTileEntity(this.getPos(), 42, updateTag));
        }
    }
}
