package zone.moddev.mc.companion;

import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import zone.moddev.mc.lib.api.automation.ResourceType;

/** Concrete companion content; the library hands its host only the pending resource data. */
public final class RecoveryCapsuleItem extends Item {
  private static final String KEY = "mmdlib_recovery";

  public RecoveryCapsuleItem() {
    super(new Item.Properties().stacksTo(1));
  }

  public static ItemStack create(CompoundTag payload) {
    var stack = new ItemStack(CompanionAddons.RECOVERY_CAPSULE.get());
    var data = new CompoundTag();
    data.put(KEY, payload.copy());
    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    return stack;
  }

  public static CompoundTag contents(ItemStack stack) {
    return stack
        .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
        .copyTag()
        .getCompound(KEY);
  }

  /**
   * Partial restoration leaves every unaccepted resource on the same item, including in creative.
   */
  public static boolean restore(ItemStack stack, ProcessorEntity target) {
    if (!stack.is(CompanionAddons.RECOVERY_CAPSULE.get())
        || target.getLevel() == null
        || target.getLevel().isClientSide) return false;
    var outer = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    var payload = outer.getCompound(KEY);
    var resources = payload.getList("resources", Tag.TAG_COMPOUND);
    boolean changed = false;
    for (int i = resources.size() - 1; i >= 0; i--) {
      var entry = resources.getCompound(i);
      var data = entry.getCompound("data");
      String type = entry.getString("type");
      if (type.equals(ResourceType.FLUIDS.id().toString())) {
        var fluid =
            FluidStack.parseOptional(target.getLevel().registryAccess(), data.getCompound("fluid"));
        int moved = target.fluids.fill(fluid, IFluidHandler.FluidAction.EXECUTE);
        if (moved > 0) {
          changed = true;
          fluid.shrink(moved);
          if (fluid.isEmpty()) data.remove("fluid");
          else data.put("fluid", fluid.save(target.getLevel().registryAccess()));
        }
      } else if (type.equals(ResourceType.ENERGY.id().toString())) {
        int amount = data.getInt("energy");
        int moved = (int) target.energy.insert(amount, false);
        if (moved > 0) {
          changed = true;
          if (moved == amount) data.remove("energy");
          else data.putInt("energy", amount - moved);
        }
      }
      if (data.isEmpty()) resources.remove(i);
    }
    long refund = Math.max(0, payload.getLong("refund"));
    long moved = target.energy.insert(refund, false);
    if (moved > 0) changed = true;
    if (!changed) return false;
    if (refund == moved) payload.remove("refund");
    else payload.putLong("refund", refund - moved);
    if (resources.isEmpty()) payload.remove("resources");
    if (payload.isEmpty()) stack.shrink(1);
    else {
      outer.put(KEY, payload);
      stack.set(DataComponents.CUSTOM_DATA, CustomData.of(outer));
    }
    target.setChanged();
    return true;
  }

  @Override
  public void appendHoverText(
      ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
    lines.add(Component.translatable("item.mmdlib_companion.recovery_capsule.help"));
  }
}
