package com.mcmoddev.lib.crafting.input.implementation;

import java.util.List;
import com.google.common.collect.Lists;
import com.mcmoddev.lib.crafting.input.ICraftingFluidInput;
import net.minecraftforge.fluids.FluidStack;

public class FluidStackInput extends BaseCraftingInput implements ICraftingFluidInput {
    private final FluidStack fluid;

    public FluidStackInput(String key, FluidStack fluid) {
        super(key, fluid.amount);
        this.fluid = fluid;
    }

    @Override
    public List<FluidStack> getPossibleInputs() {
        List<FluidStack> list = Lists.newArrayList();
        list.add(this.fluid);
        return list;
    }
}
