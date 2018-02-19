package com.mcmoddev.lib.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

@SuppressWarnings({"unused", "WeakerAccess"})
public class NBTBasedTileMessage extends NBTBasedMessage {
    private int dimensionId;
    private int posX;
    private int posY;
    private int posZ;

    public NBTBasedTileMessage() {
        super();
    }

    public NBTBasedTileMessage(final TileEntity entity, final NBTTagCompound compound) {
        this(entity.getWorld().provider.getDimension(),
            entity.getPos().getX(), entity.getPos().getY(), entity.getPos().getZ(),
            compound);
    }

    public NBTBasedTileMessage(final int dimensionId, final int posX, final int posY, final int posZ, final NBTTagCompound compound) {
        super(compound);
        this.dimensionId = dimensionId;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
    }

    public int getDimensionId() {
        return this.dimensionId;
    }

    public int getPosX() {
        return this.posX;
    }

    public int getPosY() {
        return this.posY;
    }

    public int getPosZ() {
        return this.posZ;
    }

    @Override
    public void fromBytes(final ByteBuf buf) {
        this.dimensionId = buf.readInt();
        this.posX = buf.readInt();
        this.posY = buf.readInt();
        this.posZ = buf.readInt();
        super.fromBytes(buf);
    }

    @Override
    public void toBytes(final ByteBuf buf) {
        buf.writeInt(this.dimensionId);
        buf.writeInt(this.posX);
        buf.writeInt(this.posY);
        buf.writeInt(this.posZ);
        super.toBytes(buf);
    }
}
