package com.mcmoddev.lib.inventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

public class CombinedFluidHandler implements IFluidHandler {
    private final List<IFluidHandler> handlers = new ArrayList<>();

    public CombinedFluidHandler(final IFluidHandler... handlers) {
        this.handlers.addAll(Arrays.asList(handlers));
    }

    public void addFluidHandler(final IFluidHandler handler) {
        this.handlers.add(handler);
    }

    @Override
    public IFluidTankProperties[] getTankProperties() {
        final List<IFluidTankProperties> tanks = new ArrayList<>();

        for(final IFluidHandler handler: this.handlers) {
            tanks.addAll(Arrays.asList(handler.getTankProperties()));
        }

        return tanks.toArray(new IFluidTankProperties[0]);
    }

    @Override
    public int fill(final FluidStack resource, final boolean doFill) {
        if ((resource == null) || (resource.amount == 0)) {
            return 0;
        }

        int filled = 0;
        final FluidStack remaining = resource.copy();
        for(final IFluidHandler handler: this.handlers) {
            final int current = handler.fill(remaining, doFill);
            if (current > 0) {
                filled += current;
                remaining.amount = Math.max(0, remaining.amount - current);
            }

            if (remaining.amount == 0) {
                break;
            }
        }
        return filled;
    }

    @Nullable
    @Override
    public FluidStack drain(final FluidStack resource, final boolean doDrain) {
        if ((resource == null) || (resource.amount == 0)) {
            return null;
        }

        int drained = 0;
        final FluidStack toDrain = resource.copy();
        for(final IFluidHandler handler: this.handlers) {
            final FluidStack current = handler.drain(toDrain, doDrain);
            if ((current != null) && (current.amount > 0)) {
                drained += current.amount;
                toDrain.amount = Math.max(0, toDrain.amount - current.amount);
            }

            if (toDrain.amount == 0) {
                break;
            }
        }

        toDrain.amount = drained;
        return (toDrain.amount == 0) ? null : toDrain;
    }

    @Nullable
    @Override
    public FluidStack drain(final int maxDrain, final boolean doDrain) {
        final FluidStack toDrain = Arrays.stream(this.getTankProperties())
            .filter(t -> (t.canDrain() && (t.getContents() != null) && (t.getContents().amount > 0)))
            .map(IFluidTankProperties::getContents)
            .findFirst()
            .orElse(null);

        if (toDrain == null) {
            return null;
        }

        toDrain.amount = maxDrain;
        return this.drain(toDrain, doDrain);
    }
}
