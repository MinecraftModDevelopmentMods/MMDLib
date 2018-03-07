package com.mcmoddev.lib.energy.rf;

import javax.annotation.Nullable;
import com.mcmoddev.lib.energy.ForgeEnergyValue;
import com.mcmoddev.lib.energy.IEnergyAdapter;
import com.mcmoddev.lib.energy.IEnergyValue;
import com.mcmoddev.lib.energy.IForgeEnergyCompatible;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import cofh.redstoneflux.api.IEnergyContainerItem;
import cofh.redstoneflux.api.IEnergyProvider;
import cofh.redstoneflux.api.IEnergyReceiver;

public class RFEnergySystem implements IForgeEnergyCompatible<Integer> {
    @Override
    public String getDependencyModId() {
        return "tesla";
    }

    @Override
    @Nullable
    public ForgeEnergyValue convertToFE(final IEnergyValue value) {
        if (value instanceof RFEnergyValue) {
            return new ForgeEnergyValue(RFEnergyValue.class.cast(value).getValue());
        }
        return null;
    }

    @Override
    @Nullable
    public RFEnergyValue convertFromFE(final ForgeEnergyValue value) {
        return new RFEnergyValue(value.getValue());
    }

    @Nullable
    @Override
    public IEnergyAdapter createAdapter(final TileEntity tile, final EnumFacing facing) {
        if (!this.isAvailable()) return null;

        final IEnergyReceiver receiver = (tile instanceof IEnergyReceiver) ? IEnergyReceiver.class.cast(tile) : null;
        final IEnergyProvider provider = (tile instanceof IEnergyProvider) ? IEnergyProvider.class.cast(tile) : null;
        if ((receiver != null) || (provider != null)) {
            return new RFEnergyAdapter(receiver, provider, facing);
        }

        return null;
    }

    @Nullable
    @Override
    public IEnergyAdapter createAdapter(final ItemStack stack) {
        if (!this.isAvailable()) return null;

        final IEnergyContainerItem container = (stack.getItem() instanceof IEnergyContainerItem)
            ? IEnergyContainerItem.class.cast(stack.getItem())
            : null;
        if (container != null) {
            return new RFEnergyAdapterItem(container, stack);
        }

        return null;
    }

    @Nullable
    public RFEnergyValue convertToRF(final IEnergyValue value) {
        return RFEnergyValue.class.cast(this.convertFrom(value));
    }
}
