package com.mcmoddev.lib.energy.tesla;

import java.util.Objects;
import javax.annotation.Nullable;
import com.mcmoddev.lib.energy.EnergySystemRegistry;
import com.mcmoddev.lib.energy.ForgeEnergyValue;
import com.mcmoddev.lib.energy.IEnergyAdapter;
import com.mcmoddev.lib.energy.IEnergyValue;
import com.mcmoddev.lib.energy.IForgeEnergyCompatible;
import com.mcmoddev.lib.energy.IGenericEnergyStorage;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.darkhax.tesla.api.ITeslaConsumer;
import net.darkhax.tesla.api.ITeslaHolder;
import net.darkhax.tesla.api.ITeslaProducer;
import net.darkhax.tesla.capability.TeslaCapabilities;

public class TeslaEnergySystem implements IForgeEnergyCompatible<Long> {
    @Override
    public String getDependencyModId() {
        return "tesla";
    }

    @Override
    @Nullable
    public ForgeEnergyValue convertToFE(final IEnergyValue value) {
        if (value instanceof TeslaEnergyValue) {
            return new ForgeEnergyValue((int)value.getValue());
        }
        return null;
    }

    @Override
    @Nullable
    public TeslaEnergyValue convertFromFE(final ForgeEnergyValue value) {
        return new TeslaEnergyValue(value.getValue());
    }

    @Nullable
    @Override
    public IEnergyAdapter createAdapter(final TileEntity tile, final EnumFacing facing) {
        if (!this.isAvailable()) return null;

        final ITeslaConsumer consumer = tile.getCapability(TeslaCapabilities.CAPABILITY_CONSUMER, facing);
        final ITeslaProducer producer = tile.getCapability(TeslaCapabilities.CAPABILITY_PRODUCER, facing);
        final ITeslaHolder holder = tile.getCapability(TeslaCapabilities.CAPABILITY_HOLDER, facing);
        if ((consumer != null) || (producer != null) && (holder != null)) {
            return new TeslaEnergyAdapter(consumer, holder, producer);
        }

        return null;
    }

    @Nullable
    @Override
    public IEnergyAdapter createAdapter(final ItemStack stack) {
        if (!this.isAvailable()) return null;

        final ITeslaConsumer consumer = stack.getCapability(TeslaCapabilities.CAPABILITY_CONSUMER, null);
        final ITeslaProducer producer = stack.getCapability(TeslaCapabilities.CAPABILITY_PRODUCER, null);
        final ITeslaHolder holder = stack.getCapability(TeslaCapabilities.CAPABILITY_HOLDER, null);
        if ((consumer != null) || (producer != null) && (holder != null)) {
            return new TeslaEnergyAdapter(consumer, holder, producer);
        }

        return null;
    }

    @Nullable
    public TeslaEnergyValue convertToTesla(final IEnergyValue value) {
        return TeslaEnergyValue.class.cast(this.convertFrom(value));
    }

    @Override
    public boolean hasCapability(final Capability<?> capability, final IGenericEnergyStorage storage) {
        return this.isAvailable() && EnergySystemRegistry.TESLA.isCompatibleWith(storage.getBaseSystem()) && (
            (capability == TeslaCapabilities.CAPABILITY_HOLDER)
                || (capability == TeslaCapabilities.CAPABILITY_PRODUCER)
                || (capability == TeslaCapabilities.CAPABILITY_CONSUMER)
        );
    }

    @Nullable
    @Override
    public <C> C getCapability(final Capability<C> capability, final IGenericEnergyStorage storage) {
        if (!this.hasCapability(capability, storage)) {
            return null;
        }

        if (capability == TeslaCapabilities.CAPABILITY_HOLDER) {
            return TeslaCapabilities.CAPABILITY_HOLDER.cast(new CapabilityHolder(storage));
        }

        if (capability == TeslaCapabilities.CAPABILITY_PRODUCER) {
            return TeslaCapabilities.CAPABILITY_PRODUCER.cast(new CapabilityProducer(storage));
        }

        if (capability == TeslaCapabilities.CAPABILITY_CONSUMER) {
            return TeslaCapabilities.CAPABILITY_CONSUMER.cast(new CapabilityConsumer(storage));
        }

        return null;
    }

    private static class CapabilityHolder implements ITeslaHolder {
        private final IGenericEnergyStorage storage;

        CapabilityHolder(final IGenericEnergyStorage storage) {
            this.storage = storage;
        }

        @Override
        public long getStoredPower() {
            return Objects.requireNonNull(EnergySystemRegistry.TESLA.convertFrom(this.storage.getStoredValue())).getValue();
        }

        @Override
        public long getCapacity() {
            return Objects.requireNonNull(EnergySystemRegistry.TESLA.convertFrom(this.storage.getCapacityValue())).getValue();
        }
    }

    private static class CapabilityProducer implements ITeslaProducer {
        private final IGenericEnergyStorage storage;

        CapabilityProducer(final IGenericEnergyStorage storage) {
            this.storage = storage;
        }

        @Override
        public long takePower(final long power, final boolean simulated) {
            return Objects.requireNonNull(EnergySystemRegistry.TESLA.convertFrom(this.storage.takeValue(new TeslaEnergyValue(power), !simulated))).getValue();
        }
    }

    private static class CapabilityConsumer implements ITeslaConsumer {
        private final IGenericEnergyStorage storage;

        CapabilityConsumer(final IGenericEnergyStorage storage) {
            this.storage = storage;
        }

        @Override
        public long givePower(final long power, final boolean simulated) {
            return Objects.requireNonNull(EnergySystemRegistry.TESLA.convertFrom(this.storage.storeValue(new TeslaEnergyValue(power), !simulated))).getValue();
        }
    }
}
