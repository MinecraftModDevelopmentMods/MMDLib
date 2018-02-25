package com.mcmoddev.lib.energy.tesla;

import java.text.NumberFormat;
import com.mcmoddev.lib.energy.BaseEnergyValue;
import com.mcmoddev.lib.energy.EnergySystemRegistry;
import com.mcmoddev.lib.energy.IEnergyValue;

public class TeslaEnergyValue extends BaseEnergyValue<Long> {
    public TeslaEnergyValue(final long value) {
        super(EnergySystemRegistry.TESLA, value);
    }

    @Override
    public IEnergyValue<Long> add(final IEnergyValue other) {
        if (!this.isCompatible(other)) {
            // TODO: throw something?
            return this;
        }

        final TeslaEnergyValue otherTesla = EnergySystemRegistry.TESLA.convertToTesla(other);
        return new TeslaEnergyValue(this.getValue() + ((otherTesla == null) ? 0 : otherTesla.getValue()));
    }

    @Override
    public IEnergyValue<Long> substract(final IEnergyValue other) {
        if (!this.isCompatible(other)) {
            // TODO: throw something?
            return this;
        }

        final TeslaEnergyValue otherTesla = EnergySystemRegistry.TESLA.convertToTesla(other);
        return new TeslaEnergyValue(this.getValue() - ((otherTesla == null) ? 0 : otherTesla.getValue()));
    }

    @Override
    public IEnergyValue<Long> copy() {
        return new TeslaEnergyValue(this.getValue());
    }

    @Override
    public String toString() {
        return NumberFormat.getNumberInstance().format(this.getValue()) + " T";
    }
}
