package zone.moddev.mc.lib.gui.runtime;

import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.items.IItemHandler;

import zone.moddev.mc.lib.api.capabilities.PlannedItemHandler;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;

/**
 * The client's copy of the slots behind a {@link MachineMenu}. Vanilla's menu
 * sync fills it; what each slot accepts and how much it holds come from the
 * client block entity's own {@link ScreenHost#screenItems()}, so a click is
 * predicted the way the server will answer it. The server still decides: a
 * filter that depends on server-only state can mispredict, and the server's
 * answer replaces the prediction.
 *
 * <p>
 * Reservations arrive as each slot's {@link SlotPlan} and apply the same way
 * they do in the server's storage.
 */
public final class ClientMenuItems extends PlannedItemHandler
    implements zone.moddev.mc.lib.api.capabilities.ReservationReceiver {
    private final IItemHandler rules;

    /**
     * @param size
     *            the menu's slot count, which ghost slots can take past the end
     *            of {@code rules}
     * @param rules
     *            asked what each slot accepts; its contents aren't read or
     *            changed
     */
    public ClientMenuItems(int size, IItemHandler rules) {
        super(size);
        this.rules = rules;
    }

    /**
     * Replaces what the machine plans for a slot; a slot past the end of the
     * rules is ignored.
     */
    @Override
    public void setPlan(int slot, SlotPlan plan) {
        if (inRange(slot))
            super.setPlan(slot, plan);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slot < rules.getSlots() && rules.isItemValid(slot, stack)
            && super.isItemValid(slot, stack);
    }

    private boolean inRange(int slot) {
        return slot >= 0 && slot < Math.min(getSlots(), rules.getSlots());
    }

    @Override
    public int getSlotLimit(int slot) {
        return slot < rules.getSlots() ? rules.getSlotLimit(slot) : 0;
    }
}
