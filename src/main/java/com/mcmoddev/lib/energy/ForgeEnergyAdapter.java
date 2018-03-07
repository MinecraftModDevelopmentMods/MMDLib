package com.mcmoddev.lib.energy;

import com.mcmoddev.lib.energy.util.EnergyValueUtils;
import net.minecraftforge.energy.IEnergyStorage;

public class ForgeEnergyAdapter implements IEnergyAdapter {
    private final IEnergyStorage storage;

    public ForgeEnergyAdapter(final IEnergyStorage storage) {
        this.storage = storage;
    }

    @Override
    public IEnergySystem getSystem() {
        return EnergySystemRegistry.FORGE_ENERGY;
    }

    private IEnergyValue makeValue(final int energy) {
        return new ForgeEnergyValue(energy);
    }

    @Override
    public IEnergyValue getCapacity() {
        return this.makeValue(this.storage.getMaxEnergyStored());
    }

    @Override
    public IEnergyValue getValue() {
        return this.makeValue(this.storage.getEnergyStored());
    }

    @Override
    public IEnergyValue charge(final IEnergyValue value, final boolean doCharge) {
        int result = 0;
        if (EnergyValueUtils.canGetForgeEnergy(value)) {
            final int energy = EnergyValueUtils.getForgeEnergy(value);
            result = this.storage.receiveEnergy(energy, !doCharge);
        }

        return this.makeValue(result);
    }

    @Override
    public IEnergyValue discharge(final IEnergyValue value, final boolean doDischarge) {
        int result = 0;
        if (EnergyValueUtils.canGetForgeEnergy(value)) {
            final int energy = EnergyValueUtils.getForgeEnergy(value);
            result = this.storage.extractEnergy(energy, !doDischarge);
        }

        return this.makeValue(result);
    }
}
