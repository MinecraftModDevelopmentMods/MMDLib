package com.mcmoddev.lib.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/**
 * A message type that contains a reference to a tile entity and a nbt tag compound.
 */
public class NBTBasedTileMessage extends NBTBasedMessage {
    private int dimensionId;
    private int posX;
    private int posY;
    private int posZ;

    /**
     * Creates a new instance of NBTBasedTileMessage.
     */
    public NBTBasedTileMessage() {
        super();
    }

    /**
     * Creates a new instance of NBTBasedTileMessage.
     * @param entity The tile entity this message is for.
     * @param compound The body of the message.
     */
    public NBTBasedTileMessage(final TileEntity entity, final NBTTagCompound compound) {
        this(entity.getWorld().provider.getDimension(),
            entity.getPos().getX(), entity.getPos().getY(), entity.getPos().getZ(),
            compound);
    }

    /**
     * Creates a new instance of NBTBasedTileMessage.
     * @param dimensionId The id of the dimension this message should be sent to.
     * @param posX The x coordinate of the tile entity this message is for.
     * @param posY The y coordinate of the tile entity this message is for.
     * @param posZ The z coordinate of the tile entity this message is for.
     * @param compound The body of the message.
     */
    public NBTBasedTileMessage(final int dimensionId, final int posX, final int posY, final int posZ, final NBTTagCompound compound) {
        super(compound);
        this.dimensionId = dimensionId;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
    }

    /**
     * Returns the id of the dimension this message is for.
     * @return The id of the dimension this message is for.
     */
    public int getDimensionId() {
        return this.dimensionId;
    }

    /**
     * Returns the x coordinate of the tile entity this message is for.
     * @return The x coordinate of the tile entity this message is for.
     */
    public int getPosX() {
        return this.posX;
    }

    /**
     * Returns the y coordinate of the tile entity this message is for.
     * @return The y coordinate of the tile entity this message is for.
     */
    public int getPosY() {
        return this.posY;
    }

    /**
     * Returns the z coordinate of the tile entity this message is for.
     * @return The z coordinate of the tile entity this message is for.
     */
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
