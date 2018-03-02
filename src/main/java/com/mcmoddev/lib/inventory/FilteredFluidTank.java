package com.mcmoddev.lib.inventory;

import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;

/**
 * Basic implementation of a {@link IFilteredFluidTank}.
 */
public class FilteredFluidTank implements IFluidTank, IFluidTankModifiable, IFilteredFluidTank {
    private final IFluidTankModifiable internal;
    @Nullable
    private final Predicate<FluidStack> fillFilter;
    @Nullable
    private final Predicate<FluidStack> drainFilter;

    /**
     * Creates a new instance of FilteredFluidTank.
     * @param innerTank The tank to be filtered.
     * @param fillFilter Predicate used to test if a fluid can be inserted. If null and innerTank is not empty
     *                  it will automatically filter by the initial content of the innerTank.
     * @param drainFilter Predicate used to test if a fluid can be extracted.
     *                   A null value means you can always extract from the inner tank.
     */
    public FilteredFluidTank(final IFluidTankModifiable innerTank,
                             @Nullable Predicate<FluidStack> fillFilter,
                             @Nullable final Predicate<FluidStack> drainFilter) {
        this.internal = innerTank;
        this.drainFilter = drainFilter;

        if (fillFilter == null) {
            final FluidStack existing = this.internal.getFluid();
            if ((existing != null) && (existing.getFluid() != null)) {
                fillFilter = stack -> (stack.isFluidEqual(existing));
            }
        }
        this.fillFilter = fillFilter;
    }

    @Override
    public boolean canFill(final FluidStack fluid) {
        return (this.fillFilter == null) || this.fillFilter.test(fluid);
    }

    @Override
    public boolean canDrain(final FluidStack fluid) {
        return (this.drainFilter == null) || this.drainFilter.test(fluid);
    }

    @Nullable
    @Override
    public FluidStack getFluid() {
        return this.internal.getFluid();
    }

    @Override
    public int getFluidAmount() {
        return this.internal.getFluidAmount();
    }

    @Override
    public int getCapacity() {
        return this.internal.getCapacity();
    }

    @Override
    public FluidTankInfo getInfo() {
        return this.internal.getInfo();
    }

    @Override
    public int fill(final FluidStack resource, final boolean doFill) {
        if (!this.canFill(resource)) {
            return 0;
        }

        return this.internal.fill(resource, doFill);
    }

    @Nullable
    @Override
    public FluidStack drain(final int maxDrain, final boolean doDrain) {
        final FluidStack simulated = this.internal.drain(maxDrain, false);
        if ((simulated != null) && (simulated.amount > 0)) {
            if (!this.canDrain(simulated)) {
                return null;
            }

            if (doDrain) {
                return this.internal.drain(maxDrain, true);
            }
        }
        return simulated;
    }

    @Override
    public void setFluid(@Nullable final FluidStack fluid) {
        if ((fluid != null) && !this.canFill(fluid)) {
            throw new RuntimeException("Cannot set tank content to an unacceptable fluid.");
        }
        this.internal.setFluid(fluid);
    }
}
