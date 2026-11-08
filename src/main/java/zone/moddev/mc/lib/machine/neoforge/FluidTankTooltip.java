package zone.moddev.mc.lib.machine.neoforge;

import java.util.*;
import java.util.function.Predicate;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;

import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/** Fluid descriptions and carried-container compatibility; all checks are read-only. */
public final class FluidTankTooltip {
  private FluidTankTooltip() {}

  public static List<Component> lines(
      Component group,
      FluidStack current,
      List<Component> accepted,
      ItemStack carried,
      Predicate<FluidStack> accepts) {
    var lines = new ArrayList<Component>();
    lines.add(group.copy().withStyle(ChatFormatting.GOLD));
    lines.add(
        current.isEmpty()
            ? Component.translatable("screen.mmdlib.tank.empty").withStyle(ChatFormatting.GRAY)
            : Component.translatable(
                "screen.mmdlib.tank.contents", current.getAmount(), current.getHoverName()));
    var names = Component.empty();
    for (var name : accepted) {
      if (!names.getSiblings().isEmpty()) names.append(", ");
      names.append(name.copy());
    }
    lines.add(
        Component.translatable(
                "screen.mmdlib.tank.accepts",
                accepted.isEmpty() ? Component.translatable("screen.mmdlib.tank.none") : names)
            .withStyle(ChatFormatting.BLUE));
    if (!carried.isEmpty())
      FluidUtil.getFluidHandler(carried.copy())
          .ifPresent(
              handler -> {
                Component container =
                    carried.getItem() instanceof BucketItem
                        ? Component.translatable(Items.BUCKET.getDescriptionId())
                        : carried.getHoverName();
                var seen = new ArrayList<FluidStack>();
                for (int tank = 0; tank < handler.getTanks(); tank++) {
                  var fluid = handler.getFluidInTank(tank);
                  if (fluid.isEmpty()
                      || seen.stream()
                          .anyMatch(old -> FluidStack.isSameFluidSameComponents(old, fluid)))
                    continue;
                  seen.add(fluid.copy());
                  boolean allowed =
                      accepts.test(fluid)
                          && (current.isEmpty()
                              || FluidStack.isSameFluidSameComponents(current, fluid))
                          && !handler.drain(fluid.copy(), FluidAction.SIMULATE).isEmpty();
                  lines.add(
                      Component.translatable(
                              allowed
                                  ? "screen.mmdlib.tank.container_accepts"
                                  : "screen.mmdlib.tank.container_rejects",
                              fluid.getHoverName(),
                              container)
                          .withStyle(allowed ? ChatFormatting.BLUE : ChatFormatting.RED));
                }
              });
    return List.copyOf(lines);
  }
}
