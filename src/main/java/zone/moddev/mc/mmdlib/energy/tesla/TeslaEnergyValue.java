package zone.moddev.mc.mmdlib.energy.tesla;

import java.text.NumberFormat;
import zone.moddev.mc.mmdlib.energy.BaseEnergyValue;
import zone.moddev.mc.mmdlib.energy.EnergySystemRegistry;
import zone.moddev.mc.mmdlib.energy.IEnergyValue;

@SuppressWarnings("rawtypes")
public class TeslaEnergyValue extends BaseEnergyValue<Long> {
    public TeslaEnergyValue(final long value) {
        super(EnergySystemRegistry.TESLA, value);
    }

    @Override
    public IEnergyValue<Long> add(final IEnergyValue other) {
        if (!this.isCompatible(other)) {
            // Leave this value unchanged when the other energy system is incompatible.
            return this;
        }

        final TeslaEnergyValue otherTesla = EnergySystemRegistry.TESLA.convertToTesla(other);
        return new TeslaEnergyValue(this.getValue() + ((otherTesla == null) ? 0 : otherTesla.getValue()));
    }

    @Override
    public IEnergyValue<Long> subtract(final IEnergyValue other) {
        if (!this.isCompatible(other)) {
            // Leave this value unchanged when the other energy system is incompatible.
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
