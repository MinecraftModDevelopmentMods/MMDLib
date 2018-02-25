package com.mcmoddev.lib.energy;

import java.text.NumberFormat;

public class ForgeEnergyValue extends BaseEnergyValue<Integer> {
    public ForgeEnergyValue(final int value) {
        super(EnergySystemRegistry.FORGE_ENERGY, value);
    }

    @Override
    public IEnergyValue<Integer> add(final IEnergyValue other) {
        if (!this.isCompatible(other)) {
            // TODO: throw something?
            return this;
        }

        final ForgeEnergyValue otherEnergy = EnergySystemRegistry.FORGE_ENERGY.convertFrom(other);
        return new ForgeEnergyValue(this.getValue() + ((otherEnergy == null) ? 0 : otherEnergy.getValue()));
    }

    @Override
    public IEnergyValue<Integer> substract(final IEnergyValue other) {
        if (!this.isCompatible(other)) {
            // TODO: throw something?
            return this;
        }

        final ForgeEnergyValue otherEnergy = EnergySystemRegistry.FORGE_ENERGY.convertFrom(other);
        return new ForgeEnergyValue(this.getValue() - ((otherEnergy == null) ? 0 : otherEnergy.getValue()));
    }

    @Override
    public IEnergyValue<Integer> copy() {
        return new ForgeEnergyValue(this.getValue());
    }

    @Override
    public String toString() {
        return NumberFormat.getNumberInstance().format(this.getValue()) + " FE";
    }
}
