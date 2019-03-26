package com.mcmoddev.lib.util;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import mcp.MethodsReturnNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class FluidUtils {
    // TODO: make fill/drain more generic so they both use the same code... just reversed.

    private FluidUtils() { }

    @Nullable
    public static IFluidHandlerItem getHandler(final ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        return stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
    }

    public static boolean canFillFrom(final IFluidTank tank, final ItemStack source) {
        return FluidUtils.canFillFrom(tank, source, 0, Fluid.BUCKET_VOLUME);
    }

    public static boolean canFillFrom(final IFluidTank tank, final ItemStack source, final int minAmount, final int maxAmount) {
        final IFluidHandlerItem sourceHandler = FluidUtils.getHandler(source);
        return FluidUtils.canFillFrom(tank, sourceHandler, minAmount, maxAmount);
    }

    public static boolean canFillFrom(final IFluidTank tank, final @Nullable IFluidHandlerItem source) {
        return FluidUtils.canFillFrom(tank, source, 0, Fluid.BUCKET_VOLUME);
    }

    public static boolean canFillFrom(final IFluidTank tank, @Nullable final IFluidHandlerItem source, final int minAmount, final int maxAmount) {
        if (source == null) {
            return false;
        }

        final int actualMin = (minAmount <= 0) ? maxAmount : minAmount;

        final FluidStack fluid = source.drain(maxAmount, false);
        return (fluid != null) && (fluid.amount >= actualMin) && (tank.fill(fluid, false) >= actualMin);
    }

    public static ItemStack fillFrom(final IFluidTank tank, final ItemStack source) {
        return FluidUtils.fillFrom(tank, source, 0, Fluid.BUCKET_VOLUME);
    }

    public static ItemStack fillFrom(final IFluidTank tank, final ItemStack source, final int minAmount, final int maxAmount) {
        final IFluidHandlerItem sourceHandler = FluidUtils.getHandler(source);
        return FluidUtils.fillFrom(tank, sourceHandler, minAmount, maxAmount);
    }

    public static ItemStack fillFrom(final IFluidTank tank, @Nullable final IFluidHandlerItem source) {
        return FluidUtils.fillFrom(tank, source, 0, Fluid.BUCKET_VOLUME);
    }

    public static ItemStack fillFrom(final IFluidTank tank, @Nullable final IFluidHandlerItem source, final int minAmount, final int maxAmount) {
        if (source == null) {
            return ItemStack.EMPTY;
        }

        final int actualMin = (minAmount <= 0) ? maxAmount : minAmount;

        final FluidStack fluid = source.drain(maxAmount, false);
        if ((fluid != null) && (fluid.amount >= actualMin)) {
            final int filled = tank.fill(fluid, false);
            if (filled >= actualMin) {
                tank.fill(fluid, true);
                source.drain(filled, true);
            }
        }
        return source.getContainer();
    }

    public static boolean canDrainInto(final IFluidTank tank, final ItemStack target) {
        return FluidUtils.canDrainInto(tank, target, 0, Fluid.BUCKET_VOLUME);
    }

    public static boolean canDrainInto(final IFluidTank tank, final ItemStack target, final int minAmount, final int maxAmount) {
        final IFluidHandlerItem sourceHandler = FluidUtils.getHandler(target);
        return FluidUtils.canDrainInto(tank, sourceHandler, minAmount, maxAmount);
    }

    public static boolean canDrainInto(final IFluidTank tank, final @Nullable IFluidHandlerItem target) {
        return FluidUtils.canDrainInto(tank, target, 0, Fluid.BUCKET_VOLUME);
    }

    public static boolean canDrainInto(final IFluidTank tank, @Nullable final IFluidHandlerItem target, final int minAmount, final int maxAmount) {
        if (target == null) {
            return false;
        }

        final int actualMin = (minAmount <= 0) ? maxAmount : minAmount;

        final FluidStack fluid = tank.drain(maxAmount, false);
        return (fluid != null) && (fluid.amount >= minAmount) && (target.fill(fluid, false) >= minAmount);
    }

    public static ItemStack drainInto(final IFluidTank tank, final ItemStack target) {
        return FluidUtils.drainInto(tank, target, 0, Fluid.BUCKET_VOLUME);
    }

    public static ItemStack drainInto(final IFluidTank tank, final ItemStack target, final int minAmount, final int maxAmount) {
        final IFluidHandlerItem sourceHandler = FluidUtils.getHandler(target);
        return FluidUtils.drainInto(tank, sourceHandler, minAmount, maxAmount);
    }

    public static ItemStack drainInto(final IFluidTank tank, @Nullable final IFluidHandlerItem target) {
        return FluidUtils.drainInto(tank, target, 0, Fluid.BUCKET_VOLUME);
    }

    public static ItemStack drainInto(final IFluidTank tank, @Nullable final IFluidHandlerItem target, final int minAmount, final int maxAmount) {
        if (target == null) {
            return ItemStack.EMPTY;
        }

        final int actualMin = (minAmount <= 0) ? maxAmount : minAmount;

        final FluidStack fluid = tank.drain(maxAmount, false);
        if ((fluid != null) && (fluid.amount >= actualMin)) {
            final int filled = target.fill(fluid, false);
            if (filled >= actualMin) {
                target.fill(fluid, true);
                tank.drain(filled, true);
            }
        }
        return target.getContainer();
    }
}
