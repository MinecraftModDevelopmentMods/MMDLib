package com.mcmoddev.lib.inventory;

import javax.annotation.Nullable;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

public class FluidTankHandler implements IFluidHandler {
    private final IFluidTank tank;
    private final FluidTankPropertiesWrapper properties = new FluidTankPropertiesWrapper();

    public FluidTankHandler(final IFluidTank tank) {
        this.tank = tank;
    }

    @Override
    public IFluidTankProperties[] getTankProperties() {
        return new IFluidTankProperties[] { this.properties };
    }

    @Override
    public int fill(final FluidStack resource, final boolean doFill) {
        return this.tank.fill(resource, doFill);
    }

    @Nullable
    @Override
    public FluidStack drain(final FluidStack resource, final boolean doDrain) {
        if (resource != null) {
            final FluidStack existing = this.tank.getFluid();
            if ((existing != null) && existing.isFluidEqual(resource)) {
                return this.tank.drain(resource.amount, doDrain);
            }
        }
        return null;
    }

    @Nullable
    @Override
    public FluidStack drain(final int maxDrain, final boolean doDrain) {
        return this.tank.drain(maxDrain, doDrain);
    }

    // This class only exists because mojang/forge screwed up with their wrapper and made it for FluidTank and not IFluidTank.
    private class FluidTankPropertiesWrapper implements IFluidTankProperties
    {
        FluidTankPropertiesWrapper()
        { }

        @Nullable
        @Override
        public FluidStack getContents()
        {
            final FluidStack contents = FluidTankHandler.this.tank.getFluid();
            return contents == null ? null : contents.copy();
        }

        @Override
        public int getCapacity()
        {
            return FluidTankHandler.this.tank.getCapacity();
        }

        @Override
        public boolean canFill() {
            // TODO: maybe make a new interface for this
            final IFluidTank tank = FluidTankHandler.this.tank;
            return !(tank instanceof FluidTank) || ((FluidTank) tank).canFill();
        }

        @Override
        public boolean canDrain()
        {
            // TODO: maybe make a new interface for this
            final IFluidTank tank = FluidTankHandler.this.tank;
            return !(tank instanceof FluidTank) || ((FluidTank) tank).canDrain();
        }

        @Override
        public boolean canFillFluidType(final FluidStack fluidStack)
        {
            // TODO: maybe make a new interface for this
            final IFluidTank tank = FluidTankHandler.this.tank;
            return !(tank instanceof FluidTank) || ((FluidTank) tank).canFillFluidType(fluidStack);
        }

        @Override
        public boolean canDrainFluidType(final FluidStack fluidStack)
        {
            // TODO: maybe make a new interface for this
            final IFluidTank tank = FluidTankHandler.this.tank;
            return !(tank instanceof FluidTank) || ((FluidTank) tank).canDrainFluidType(fluidStack);
        }
    }
}
