package zone.moddev.mc.lib.machine.neoforge;

import net.neoforged.neoforge.fluids.FluidStack;

import zone.moddev.mc.lib.machine.ingredient.FluidAmount;

/**
 * Converts between the machine core's {@link FluidAmount} and NeoForge's
 * {@link FluidStack}.
 */
public final class FluidStacks {
    private FluidStacks() {
    }

    /** The stack for {@code fluid}; empty for {@link FluidAmount#EMPTY}. */
    public static FluidStack stack(FluidAmount fluid) {
        return new FluidStack(fluid.fluid(), fluid.amount(),
            fluid.components());
    }

    /**
     * The amount for {@code stack}; {@link FluidAmount#EMPTY} for an empty
     * stack.
     */
    public static FluidAmount amount(FluidStack stack) {
        return stack.isEmpty()
            ? FluidAmount.EMPTY
            : new FluidAmount(stack.getFluidHolder(),
                stack.getComponentsPatch(), stack.getAmount());
    }
}
