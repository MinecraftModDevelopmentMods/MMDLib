package zone.moddev.mc.lib.common.fluid;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;

/**
 * {@code mmdlib:fluid_bottle}: a glass bottle holding {@link FluidBottles#CAPACITY} of one fluid,
 * kept in the {@link FluidBottles#CONTENT} component. Named after its fluid ("Bottle of Molten
 * Copper"); emptying it, through its fluid capability, leaves a glass bottle.
 */
public final class FluidBottleItem extends Item {
  /** One-high stacks, like potions: bottles of different fluids cannot share a stack anyway. */
  public FluidBottleItem(Properties properties) {
    super(properties.stacksTo(1).craftRemainder(Items.GLASS_BOTTLE));
  }

  /** The fluid in {@code stack}; empty for an unfilled bottle. */
  public static FluidStack contents(ItemStack stack) {
    return stack.getOrDefault(FluidBottles.CONTENT.get(), SimpleFluidContent.EMPTY).copy();
  }

  /** A bottle full of {@code fluid}. */
  public static ItemStack filled(Fluid fluid) {
    var stack = new ItemStack(FluidBottles.BOTTLE.get());
    stack.set(
        FluidBottles.CONTENT.get(),
        SimpleFluidContent.copyOf(new FluidStack(fluid, FluidBottles.CAPACITY)));
    return stack;
  }

  @Override
  public Component getName(ItemStack stack) {
    var fluid = contents(stack);
    return fluid.isEmpty()
        ? super.getName(stack)
        : Component.translatable(getDescriptionId(stack) + ".filled", fluid.getHoverName());
  }
}
