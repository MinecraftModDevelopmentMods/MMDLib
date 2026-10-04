package zone.moddev.mc.mmdlib.energy.rf;

import java.text.NumberFormat;
import zone.moddev.mc.mmdlib.energy.BaseEnergyValue;
import zone.moddev.mc.mmdlib.energy.EnergySystemRegistry;
import zone.moddev.mc.mmdlib.energy.IEnergyValue;

@SuppressWarnings("rawtypes")
public class RFEnergyValue extends BaseEnergyValue<Integer> {
    public RFEnergyValue(final int value) {
        super(EnergySystemRegistry.RF, value);
    }

    @Override
    public IEnergyValue<Integer> add(final IEnergyValue other) {
        if (!this.isCompatible(other)) {
            // Leave this value unchanged when the other energy system is incompatible.
            return this;
        }

        final RFEnergyValue otherRf = EnergySystemRegistry.RF.convertToRF(other);
        return new RFEnergyValue(this.getValue() + ((otherRf == null) ? 0 : otherRf.getValue()));
    }

    @Override
    public IEnergyValue<Integer> subtract(final IEnergyValue other) {
        if (!this.isCompatible(other)) {
            // Leave this value unchanged when the other energy system is incompatible.
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
