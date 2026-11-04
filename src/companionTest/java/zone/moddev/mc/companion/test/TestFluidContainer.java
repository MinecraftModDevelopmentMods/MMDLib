package zone.moddev.mc.companion.test;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import zone.moddev.mc.companion.CompanionMod;

/** Test-only stand-in for a modded container that transfers partial amounts, unlike buckets. */
@EventBusSubscriber(modid = CompanionMod.ID, bus = EventBusSubscriber.Bus.MOD)
public final class TestFluidContainer extends FluidTank implements IFluidHandlerItem {
  private final ItemStack container;

  private TestFluidContainer(ItemStack container) {
    super(2_000, fluid -> fluid.getFluid() == Fluids.WATER);
    this.container = container;
    int amount = container.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
        .copyTag().getInt("test_water");
    setFluid(new FluidStack(Fluids.WATER, amount));
  }

  @SubscribeEvent
  public static void capabilities(RegisterCapabilitiesEvent event) {
    event.registerItem(Capabilities.FluidHandler.ITEM,
        (stack, context) -> new TestFluidContainer(stack), Items.GLASS_BOTTLE);
  }

  @Override
  public ItemStack getContainer() {
    return container;
  }

  @Override
  protected void onContentsChanged() {
    CustomData.update(DataComponents.CUSTOM_DATA, container,
        tag -> tag.putInt("test_water", getFluidAmount()));
  }
}
