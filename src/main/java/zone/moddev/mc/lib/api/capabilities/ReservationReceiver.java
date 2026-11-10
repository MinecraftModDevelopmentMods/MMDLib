package zone.moddev.mc.lib.api.capabilities;

import zone.moddev.mc.lib.machine.inventory.SlotPlan;

/**
 * Optional storage contract for applying authoritative reservation snapshots to
 * a menu mirror.
 */
public interface ReservationReceiver {
    void setPlan(int slot, SlotPlan plan);
}
