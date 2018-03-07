package com.mcmoddev.lib.energy.rf;

import com.mcmoddev.lib.energy.EnergySystemRegistry;
import com.mcmoddev.lib.energy.IEnergyAdapter;
import com.mcmoddev.lib.energy.IEnergySystem;
import com.mcmoddev.lib.energy.IEnergyValue;
import net.minecraft.item.ItemStack;
import cofh.redstoneflux.api.IEnergyContainerItem;

public class RFEnergyAdapterItem implements IEnergyAdapter {
    private final IEnergyContainerItem container;
    private final ItemStack stack;

    public RFEnergyAdapterItem(final IEnergyContainerItem container, final ItemStack stack) {
        this.container = container;
        this.stack = stack;
    }

    @Override
    public IEnergySystem getSystem() {
        return EnergySystemRegistry.RF;
    }

    private RFEnergyValue makeValue(final int value) {
        return new RFEnergyValue(value);
    }

    @Override
    public IEnergyValue getCapacity() {
        return this.makeValue(this.container.getMaxEnergyStored(this.stack));
    }

    @Override
    public IEnergyValue getValue() {
        return this.makeValue(this.container.getEnergyStored(this.stack));
    }

    @Override
    public IEnergyValue charge(final IEnergyValue value, final boolean doCharge) {
        final IEnergyValue ours = this.getSystem().convertFrom(value);
        if (ours instanceof RFEnergyValue) {
            final int energy = ((RFEnergyValue) ours).getValue();
            return this.makeValue(this.container.receiveEnergy(this.stack, energy, !doCharge));
        }

        return this.makeValue(0);
    }

    @Override
    public IEnergyValue discharge(final IEnergyValue value, final boolean doDischarge) {
        final IEnergyValue ours = this.getSystem().convertFrom(value);
        if (ours instanceof RFEnergyValue) {
            final int energy = ((RFEnergyValue) ours).getValue();
            return this.makeValue(this.container.extractEnergy(this.stack, energy, !doDischarge));
        }

        return this.makeValue(0);
    }
}
