package com.mcmoddev.lib.energy;

import javax.annotation.Nullable;
import net.minecraft.item.ItemStack;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

public class EnergyUtils {
    // TODO: put custom energy storage here to support more than forge energy
    public static @Nullable
    IEnergyStorage getEnergyStorage(ItemStack stack) {
        if (stack.hasCapability(CapabilityEnergy.ENERGY, null)) {
            return stack.getCapability(CapabilityEnergy.ENERGY, null);
        }
        return null;
    }
}
