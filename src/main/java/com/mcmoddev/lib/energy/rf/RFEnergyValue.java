package com.mcmoddev.lib.energy.rf;

import java.text.NumberFormat;
import com.mcmoddev.lib.energy.BaseEnergyValue;
import com.mcmoddev.lib.energy.EnergySystemRegistry;
import com.mcmoddev.lib.energy.IEnergyValue;

public class RFEnergyValue extends BaseEnergyValue<Integer> {
    public RFEnergyValue(final int value) {
        super(EnergySystemRegistry.RF, value);
    }

    @Override
    public IEnergyValue<Integer> add(final IEnergyValue other) {
        if (!this.isCompatible(other)) {
            // TODO: throw something?
            return this;
        }

        final RFEnergyValue otherRf = EnergySystemRegistry.RF.convertToRF(other);
        return new RFEnergyValue(this.getValue() + ((otherRf == null) ? 0 : otherRf.getValue()));
    }

    @Override
    public IEnergyValue<Integer> substract(final IEnergyValue other) {
        if (!this.isCompatible(other)) {
            // TODO: throw something?
            return this;
        }

        final RFEnergyValue otherRf = EnergySystemRegistry.RF.convertToRF(other);
        return new RFEnergyValue(this.getValue() - ((otherRf == null) ? 0 : otherRf.getValue()));
    }

    @Override
    public IEnergyValue<Integer> copy() {
        return new RFEnergyValue(this.getValue());
    }

    @Override
    public String toString() {
        return NumberFormat.getNumberInstance().format(this.getValue()) + " RF";
    }
}
