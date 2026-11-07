package zone.moddev.mc.lib.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.model.DynamicFluidContainerModel;

import zone.moddev.mc.lib.common.fluid.FluidBottles;

/**
 * Colors the fluid in a {@code mmdlib:fluid_bottle} with whatever tint its fluid type registers on
 * the client ({@code IClientFluidTypeExtensions#getTintColor}); fluids that register none show
 * their texture as drawn.
 */
@EventBusSubscriber(modid = "mmdlib", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FluidBottleClient {
  private FluidBottleClient() {}

  @SubscribeEvent
  public static void colors(RegisterColorHandlersEvent.Item event) {
    event.register(new DynamicFluidContainerModel.Colors(), FluidBottles.BOTTLE.get());
  }
}
