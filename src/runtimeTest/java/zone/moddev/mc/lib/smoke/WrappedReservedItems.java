package zone.moddev.mc.lib.smoke;

import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.items.IItemHandlerModifiable;

import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.api.capabilities.ReservedSlots;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;

/**
 * A consumer wrapper deliberately unrelated to ItemStorage's class hierarchy.
 */
record WrappedReservedItems(ItemStorage storage)
    implements IItemHandlerModifiable, ReservedSlots {
    public int getSlots() {
        return storage.getSlots();
    }
    public ItemStack getStackInSlot(int slot) {
        return storage.getStackInSlot(slot);
    }
    public void setStackInSlot(int slot, ItemStack stack) {
        storage.setStackInSlot(slot, stack);
    }
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return storage.insertItem(slot, stack, simulate);
    }
    public ItemStack extractItem(int slot, int count, boolean simulate) {
        return storage.extractItem(slot, count, simulate);
    }
    public int getSlotLimit(int slot) {
        return storage.getSlotLimit(slot);
    }
    public boolean isItemValid(int slot, ItemStack stack) {
        return storage.isItemValid(slot, stack);
    }
    public SlotPlan plan(int slot) {
        return storage.plan(slot);
    }
    public boolean hasPlan(int slot) {
        return storage.hasPlan(slot);
    }
    public int freeToTake(int slot) {
        return storage.freeToTake(slot);
    }
    public int maxHeld(int slot, ItemStack stack) {
        return storage.maxHeld(slot, stack);
    }
}
