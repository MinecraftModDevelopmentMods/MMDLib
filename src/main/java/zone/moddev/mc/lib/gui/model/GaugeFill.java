package zone.moddev.mc.lib.gui.model;

import java.util.Objects;
import java.util.Optional;

import net.neoforged.neoforge.fluids.FluidStack;

/** Element-selected gauge contents; client drawing resolves fluid textures and default tint. */
public record GaugeFill(Optional<FluidStack> fluid, Optional<Integer> colour) {
  public GaugeFill {
    fluid = Objects.requireNonNull(fluid).filter(stack -> !stack.isEmpty()).map(FluidStack::copy);
    Objects.requireNonNull(colour);
    if (fluid.isEmpty()) colour = Optional.of(colour.orElse(0x777777));
  }

  /** Returns a copy so rendering cannot mutate the selected contents. */
  @Override
  public Optional<FluidStack> fluid() {
    return fluid.map(FluidStack::copy);
  }

  /** A solid RGB fill, independent of the synchronized value's type. */
  public static GaugeFill solid(int rgb) {
    return new GaugeFill(Optional.empty(), Optional.of(rgb));
  }

  /** A textured fluid fill using its client tint, or grey for an empty stack. */
  public static GaugeFill fluid(FluidStack fluid) {
    return new GaugeFill(Optional.of(fluid), Optional.empty());
  }

  /** A textured fluid fill with an explicit ARGB tint. */
  public static GaugeFill fluid(FluidStack fluid, int argb) {
    return new GaugeFill(Optional.of(fluid), Optional.of(argb));
  }
}
