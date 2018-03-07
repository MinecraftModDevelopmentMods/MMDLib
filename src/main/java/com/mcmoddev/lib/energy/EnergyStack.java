package com.mcmoddev.lib.energy;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.INBTSerializable;

public class EnergyStack implements INBTSerializable<NBTTagCompound> {
    public static final EnergyStack EMPTY = new EnergyStack(0);
    private static final String ENERGY_NBT_KEY = "energy";

    private long energy;

    public EnergyStack(long energy) {
        this.energy = energy;
    }

    public EnergyStack(NBTTagCompound nbt) {
        this.deserializeNBT(nbt);
    }

    public Long getEnergy() {
        return this.energy;
    }

    public void setEnergy(Long energy) {
        this.energy = energy;
    }

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setLong(ENERGY_NBT_KEY, this.energy);
        return nbt;
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        if ((nbt != null) && nbt.hasKey(ENERGY_NBT_KEY, Constants.NBT.TAG_LONG)) {
            this.energy = nbt.getLong(ENERGY_NBT_KEY);
        }
        else {
            this.energy = 0L;
        }
    }
}
