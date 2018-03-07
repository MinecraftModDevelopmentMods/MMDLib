package com.mcmoddev.lib.crafting.input;

import javax.annotation.Nullable;
import com.mcmoddev.lib.energy.EnergyStack;

public interface ICraftingEnergyInput extends ITypedCraftingInput<EnergyStack> {
    @Override
    default Class<EnergyStack> getInputClass() {
        return EnergyStack.class;
    }

    @Nullable
    @Override
    default EnergyStack getNullInput() {
        return EnergyStack.EMPTY;
    }
}