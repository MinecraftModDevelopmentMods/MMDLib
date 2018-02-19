package com.mcmoddev.lib.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

public class SimpleFluidTank extends FluidTank implements IFluidTankModifiable, IResponsiveFluidTank {
    private final List<Consumer<IResponsiveFluidTank>> responsiveTargets = new ArrayList<>();

    public SimpleFluidTank(final int capacity) {
        super(capacity);
    }

    public SimpleFluidTank(@Nullable final FluidStack fluidStack, final int capacity) {
        super(fluidStack, capacity);
    }

    public SimpleFluidTank(final Fluid fluid, final int amount, final int capacity) {
        super(fluid, amount, capacity);
    }

    @Override
    public <T extends IResponsiveFluidTank> void addResponsiveTarget(final Consumer<T> consumer) {
        // TODO: find a fix for this 'hack'
        //noinspection unchecked
        this.responsiveTargets.add((Consumer<IResponsiveFluidTank>) consumer);
    }

    @Override
    protected void onContentsChanged() {
        super.onContentsChanged();

        this.responsiveTargets.forEach(t -> t.accept(this));
    }
}
