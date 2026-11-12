package zone.moddev.mc.lib.common.fluid;

import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

/**
 * The fluid capability of an empty glass bottle: it takes exactly
 * {@link FluidBottles#capacity} of a bottleable or experience fluid at once and
 * becomes {@link FluidBottles#filled}, the way NeoForge's bucket wrapper turns
 * an empty bucket into a filled one. Anything else is refused, so other fluid
 * handling of glass bottles is left alone.
 */
final class EmptyBottleFluidHandler implements IFluidHandlerItem {
    private ItemStack container;

    EmptyBottleFluidHandler(ItemStack container) {
        this.container = container;
    }

    @Override
    public ItemStack getContainer() {
        return container;
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        // Not knowing the fluid, the larger of the two: experience bottles take
        // less.
        return Math.max(FluidBottles.CAPACITY,
            FluidBottles.EXPERIENCE_CAPACITY);
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return FluidBottles.capacity(stack.getFluid()) > 0;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        // A single bottle, filled whole: a stack of bottles is filled one at a
        // time by FluidUtil.
        var capacity = FluidBottles.capacity(resource.getFluid());
        if (container.getCount() != 1 || capacity == 0
            || resource.getAmount() < capacity)
            return 0;
        if (action.execute())
            container = FluidBottles.filled(resource.getFluid());
        return capacity;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return FluidStack.EMPTY;
    }
}
