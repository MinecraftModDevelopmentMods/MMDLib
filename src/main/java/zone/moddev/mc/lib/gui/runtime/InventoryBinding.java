package zone.moddev.mc.lib.gui.runtime;

import java.util.List;
import java.util.Objects;

import net.neoforged.neoforge.items.IItemHandlerModifiable;

import zone.moddev.mc.lib.api.capabilities.ItemInventories;

/** A resolved physical slot and its menu-facing addresses; no storage implementation is assumed. */
public record InventoryBinding(
    IItemHandlerModifiable storage,
    int slot,
    int legacyIndex,
    List<ItemInventories.Address> addresses) {
  public InventoryBinding {
    Objects.requireNonNull(storage);
    addresses = List.copyOf(addresses);
    if (slot < 0 || slot >= storage.getSlots())
      throw new IllegalArgumentException(
          "Physical slot outside storage of size " + storage.getSlots());
    if (legacyIndex < -1 || legacyIndex >= 32767)
      throw new IllegalArgumentException("Legacy slot index outside -1..32766");
  }
}
