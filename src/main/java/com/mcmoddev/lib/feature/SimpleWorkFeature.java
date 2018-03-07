package com.mcmoddev.lib.feature;

import java.util.function.Supplier;
import com.mcmoddev.lib.energy.ForgeEnergyStorage;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ITickable;

public class SimpleWorkFeature extends BaseFeature implements ITickable {
    private final Supplier<Integer> canStartDelegate;
    private final Supplier<Boolean> endProcessDelegate;
    private final ForgeEnergyStorage battery;
    private final int batteryDischargeRate;

    private boolean isRunning = false;
    private int totalEnergyRequired = 0;
    private int currentEnergy = 0;

    public SimpleWorkFeature(final String key,
                             final ForgeEnergyStorage battery,
                             final int batteryDischargeRate,
                             final Supplier<Integer> canStartDelegate,
                             final Supplier<Boolean> endProcessDelegate) {
        super(key);

        this.battery = battery;
        this.batteryDischargeRate = batteryDischargeRate;
        this.canStartDelegate = canStartDelegate;
        this.endProcessDelegate = endProcessDelegate;
    }

    @Override
    protected void writeToNBT(final NBTTagCompound tag) {
        tag.setBoolean("is_running", this.isRunning);
        tag.setInteger("required_energy", this.totalEnergyRequired);
        tag.setInteger("energy", this.currentEnergy);
    }

    @Override
    public void deserializeNBT(final NBTTagCompound nbt) {
        this.isRunning = nbt.getBoolean("is_running");
        this.totalEnergyRequired = nbt.getInteger("required_energy");
        this.currentEnergy = nbt.getInteger("energy");
    }

    @Override
    public void update() {
        if (!this.isRunning) {
            final int energyRequired = this.canStartDelegate.get();
            if (energyRequired > 0) {
                this.isRunning = true;
                this.totalEnergyRequired = energyRequired;
                this.currentEnergy = 0;
                this.setDirty(FeatureDirtyLevel.GUI);
            }
        }

        if (this.isRunning) {
            // try consume
            final int toConsume = Math.min(this.batteryDischargeRate, this.totalEnergyRequired - this.currentEnergy);
            if ((toConsume <= 0) || (this.battery.take(toConsume, false) > 0)) {
                final int taken = (toConsume > 0)
                    ? this.battery.take(toConsume, true)
                    : 0;
                if (taken > 0) {
                    this.currentEnergy += taken;
                    this.setDirty(FeatureDirtyLevel.GUI);
                }

                if ((this.currentEnergy >= this.totalEnergyRequired) && this.endProcessDelegate.get()) {
                    this.isRunning = false;
                    this.totalEnergyRequired = 0;
                    this.currentEnergy = 0;
                    this.setDirty(FeatureDirtyLevel.GUI);
                }
            }
        }
    }
}
