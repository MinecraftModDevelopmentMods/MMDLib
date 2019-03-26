package com.mcmoddev.lib.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import com.mcmoddev.lib.MMDLib;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;

/**
 * Basic implementation for {@link IResponsiveFluidTank} and {@link IFilteredFluidTank}.
 */
@SuppressWarnings("unchecked")
public class ResponsiveFluidTankWrapper implements IFluidTankModifiable, IResponsiveFluidTank, IFluidTank, IFilteredFluidTank {
    private final List<Consumer<IResponsiveFluidTank>> responsiveTargets = new ArrayList<>();

    private final IFluidTank tank;
    private final IFluidTankModifiable modifiableTank;
    private final IFilteredFluidTank filteredTank;

    /**
     * Creates a new instance of ResponsiveFluidTankWrapper.
     * @param tank The fluid tank to be wrapped.
     * @param responsiveTarget The consumer that will receive content change notifications from this tank wrapper.
     */
    public ResponsiveFluidTankWrapper(final IFluidTank tank, final Consumer<ResponsiveFluidTankWrapper> responsiveTarget) {
        this.tank = tank;
        this.filteredTank = (this.tank instanceof IFilteredFluidTank) ? (IFilteredFluidTank)this.tank : null;
        this.modifiableTank = (this.tank instanceof IFluidTankModifiable) ? (IFluidTankModifiable)this.tank : null;

        this.addResponsiveTarget(responsiveTarget);
    }

    @Override
    public boolean canFill(final FluidStack fluid) {
        return (this.filteredTank == null) || this.filteredTank.canFill(fluid);
    }

    @Override
    public boolean canDrain(final FluidStack fluid) {
        return (this.filteredTank == null) || this.filteredTank.canDrain(fluid);
    }

    @Override
    public void setFluid(@Nullable final FluidStack fluid) {
        if (this.modifiableTank != null) {
            this.modifiableTank.setFluid(fluid);
            this.onChanged();
        } else {
            // TODO: consider throwing an error
            MMDLib.logger.error("Someone or something tried to set the fluid in a non-modifiable tank!");
        }
    }

    @Override
    public <T extends IResponsiveFluidTank> void addResponsiveTarget(final Consumer<T> consumer) {
        // TODO: find a fix for this 'hack'
        //noinspection unchecked
        this.responsiveTargets.add((Consumer<IResponsiveFluidTank>) consumer);
    }

    @Nullable
    @Override
    public FluidStack getFluid() {
        return this.tank.getFluid();
    }

    @Override
    public int getFluidAmount() {
        return this.tank.getFluidAmount();
    }

    @Override
    public int getCapacity() {
        return this.tank.getCapacity();
    }

    @Override
    public FluidTankInfo getInfo() {
        return this.tank.getInfo();
    }

    @Override
    public int fill(final FluidStack resource, final boolean doFill) {
        final int filled = this.tank.fill(resource, doFill);

        if (doFill && (filled > 0)) {
            this.onChanged();
        }

        return filled;
    }

    @Nullable
    @Override
    public FluidStack drain(final int maxDrain, final boolean doDrain) {
        final FluidStack drained = this.tank.drain(maxDrain, doDrain);

        if (doDrain && (drained != null) && (drained.amount > 0)) {
            this.onChanged();
        }

        return drained;
    }

    /**
     * Called when the content of this tank changes.
     */
    protected void onChanged() {
        this.responsiveTargets.forEach(t -> t.accept(this));
    }
}
