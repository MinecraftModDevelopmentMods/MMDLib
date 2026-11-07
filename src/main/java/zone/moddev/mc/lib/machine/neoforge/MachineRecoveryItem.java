package zone.moddev.mc.lib.machine.neoforge;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;

import zone.moddev.mc.lib.api.automation.ResourceType;
import zone.moddev.mc.lib.api.capabilities.Transfer;

/** Durable displaced-resource payload for data-defined machines. Unknown entries are retained. */
public final class MachineRecoveryItem extends Item {
  private static final String KEY = "mmdlib_recovery";

  public MachineRecoveryItem() {
    super(new Item.Properties().stacksTo(1));
  }

  public static ItemStack create(CompoundTag payload) {
    var stack = new ItemStack(MachineContent.recoveryItem());
    var data = new CompoundTag();
    data.put(KEY, payload.copy());
    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    return stack;
  }

  public static boolean restore(ItemStack stack, DefinedMachineEntity target) {
    if (target.getLevel() == null
        || target.getLevel().isClientSide()
        || !stack.is(MachineContent.recoveryItem())) return false;
    var outer = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    var payload = outer.getCompound(KEY);
    var entries = payload.getList("resources", Tag.TAG_COMPOUND);
    boolean changed = false;
    for (int i = entries.size() - 1; i >= 0; i--) {
      var entry = entries.getCompound(i);
      var data = entry.getCompound("data");
      String type = entry.getString("type");
      if (type.equals(ResourceType.ITEMS.id().toString())) {
        var item =
            ItemStack.parseOptional(target.getLevel().registryAccess(), data.getCompound("item"));
        if (!item.isEmpty()) {
          var remaining = Transfer.insert(target.items, item, false);
          if (remaining.getCount() < item.getCount()) {
            changed = true;
            if (remaining.isEmpty()) data.remove("item");
            else data.put("item", remaining.save(target.getLevel().registryAccess()));
          }
        }
      } else if (type.equals(ResourceType.ENERGY.id().toString())) {
        long amount = Math.max(0, data.getLong("energy")),
            moved = target.energy.insert(amount, false);
        if (moved > 0) {
          changed = true;
          if (moved == amount) data.remove("energy");
          else data.putLong("energy", amount - moved);
        }
      }
      if (data.isEmpty()) entries.remove(i);
    }
    long refund = Math.max(0, payload.getLong("refund")),
        moved = target.energy.insert(refund, false);
    if (moved > 0) changed = true;
    if (!changed) return false;
    if (moved == refund) payload.remove("refund");
    else payload.putLong("refund", refund - moved);
    if (entries.isEmpty()) payload.remove("resources");
    if (payload.isEmpty()) stack.shrink(1);
    else {
      outer.put(KEY, payload);
      stack.set(DataComponents.CUSTOM_DATA, CustomData.of(outer));
    }
    target.setChanged();
    return true;
  }
}
