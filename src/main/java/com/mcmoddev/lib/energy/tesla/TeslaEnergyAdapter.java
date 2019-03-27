package com.mcmoddev.lib.energy.tesla;

import javax.annotation.Nullable;
import com.mcmoddev.lib.energy.EnergySystemRegistry;
import com.mcmoddev.lib.energy.IEnergyAdapter;
import com.mcmoddev.lib.energy.IEnergySystem;
import com.mcmoddev.lib.energy.IEnergyValue;
import net.darkhax.tesla.api.ITeslaConsumer;
import net.darkhax.tesla.api.ITeslaHolder;
import net.darkhax.tesla.api.ITeslaProducer;

@SuppressWarnings("rawtypes")
public class TeslaEnergyAdapter implements IEnergyAdapter {
    private final ITeslaConsumer consumer;
    private final ITeslaHolder holder;
    private final ITeslaProducer producer;

    public TeslaEnergyAdapter(@Nullable final ITeslaConsumer consumer, @Nullable final ITeslaHolder holder, final @Nullable ITeslaProducer producer) {
        this.consumer = consumer;
        this.holder = holder;
        this.producer = producer;
    }

    @Override
    public IEnergySystem getSystem() {
        return EnergySystemRegistry.TESLA;
    }

    private TeslaEnergyValue makeValue(final long value) {
        return new TeslaEnergyValue(value);
    }

    @Override
    public IEnergyValue getCapacity() {
        return this.makeValue((this.holder == null) ? 0 : this.holder.getCapacity());
    }

    @Override
    public IEnergyValue getValue() {
        return this.makeValue((this.holder == null) ? 0 : this.holder.getStoredPower());
    }

    @Override
    public IEnergyValue charge(final IEnergyValue value, final boolean doCharge) {
        if (this.consumer != null) {
            final IEnergyValue ours = this.getSystem().convertFrom(value);
            if (ours instanceof TeslaEnergyValue) {
                final long energy = ((TeslaEnergyValue) ours).getValue();
                return this.makeValue(this.consumer.givePower(energy, !doCharge));
            }
        }
        return this.makeValue(0);
    }

    @Override
    public IEnergyValue discharge(final IEnergyValue value, final boolean doDischarge) {
        if (this.producer != null) {
            final IEnergyValue ours = this.getSystem().convertFrom(value);
            if (ours instanceof TeslaEnergyValue) {
                final long energy = ((TeslaEnergyValue) ours).getValue();
                return this.makeValue(this.producer.takePower(energy, !doDischarge));
            }
        }
        return this.makeValue(0);
    }
}
