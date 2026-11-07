package zone.moddev.mc.lib.common.fluid;

import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

/**
 * The fluid capability of an empty glass bottle: it takes exactly {@link FluidBottles#CAPACITY} of
 * a bottleable fluid at once and becomes a {@link FluidBottleItem}, the way NeoForge's bucket
 * wrapper turns an empty bucket into a filled one. Anything else is refused, so other fluid
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
    return FluidBottles.CAPACITY;
  }

  @Override
  public boolean isFluidValid(int tank, FluidStack stack) {
    return FluidBottles.bottleable(stack.getFluid());
  }

  @Override
  public int fill(FluidStack resource, FluidAction action) {
    // A single bottle, filled whole: a stack of bottles is filled one at a time by FluidUtil.
    if (container.getCount() != 1
        || resource.getAmount() < FluidBottles.CAPACITY
        || !FluidBottles.bottleable(resource.getFluid())) return 0;
    if (action.execute()) container = FluidBottleItem.filled(resource.getFluid());
    return FluidBottles.CAPACITY;
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
