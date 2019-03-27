package com.mcmoddev.lib.energy.rf;

import javax.annotation.Nullable;
import com.mcmoddev.lib.energy.EnergySystemRegistry;
import com.mcmoddev.lib.energy.IEnergyAdapter;
import com.mcmoddev.lib.energy.IEnergySystem;
import com.mcmoddev.lib.energy.IEnergyValue;
import net.minecraft.util.EnumFacing;
import cofh.redstoneflux.api.IEnergyHandler;
import cofh.redstoneflux.api.IEnergyProvider;
import cofh.redstoneflux.api.IEnergyReceiver;

@SuppressWarnings("rawtypes")
public class RFEnergyAdapter implements IEnergyAdapter {
    private final IEnergyReceiver receiver;
    private final IEnergyHandler handler;
    private final IEnergyProvider provider;
    private final EnumFacing facing;

    public RFEnergyAdapter(@Nullable final IEnergyReceiver receiver, final @Nullable IEnergyProvider provider, final @Nullable EnumFacing facing) {
        this.receiver = receiver;
        this.provider = provider;
        this.handler = (receiver == null) ? provider : receiver;
        this.facing = facing;
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
        return this.makeValue((this.handler == null) ? 0 : this.handler.getMaxEnergyStored(this.facing));
    }

    @Override
    public IEnergyValue getValue() {
        return this.makeValue((this.handler == null) ? 0 : this.handler.getEnergyStored(this.facing));
    }

    @Override
    public IEnergyValue charge(final IEnergyValue value, final boolean doCharge) {
        if (this.receiver != null) {
            final IEnergyValue ours = this.getSystem().convertFrom(value);
            if (ours instanceof RFEnergyValue) {
                final int energy = ((RFEnergyValue) ours).getValue();
                return this.makeValue(this.receiver.receiveEnergy(this.facing, energy, !doCharge));
            }
        }
        return this.makeValue(0);
    }

    @Override
    public IEnergyValue discharge(final IEnergyValue value, final boolean doDischarge) {
        if (this.provider != null) {
            final IEnergyValue ours = this.getSystem().convertFrom(value);
            if (ours instanceof RFEnergyValue) {
                final int energy = ((RFEnergyValue) ours).getValue();
                return this.makeValue(this.provider.extractEnergy(this.facing, energy, !doDischarge));
            }
        }
        return this.makeValue(0);
    }
}
