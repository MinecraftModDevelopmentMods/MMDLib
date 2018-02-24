package com.mcmoddev.lib.energy;

import javax.annotation.Nullable;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.Loader;

public interface IEnergySystem<T> extends IEnergyCapabilityProvider {
    String getDependencyModId();

    default boolean isAvailable() {
        return Loader.isModLoaded(this.getDependencyModId());
    }

    @Nullable
    IEnergyAdapter createAdapter(TileEntity tile, EnumFacing facing);
    @Nullable
    IEnergyAdapter createAdapter(ItemStack stack);

    boolean isCompatibleWith(IEnergySystem system);
    @Nullable
    IEnergyValue<T> convertFrom(IEnergyValue value);

    @Override
    default boolean hasCapability(final Capability<?> capability, final IGenericEnergyStorage storage) {
        return false;
    }

    @Nullable
    @Override
    default <C> C getCapability(final Capability<C> capability, final IGenericEnergyStorage storage) {
        return null;
    }
}
