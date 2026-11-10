package zone.moddev.mc.lib.gui.runtime.component;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.api.capabilities.ItemInventories;
import zone.moddev.mc.lib.gui.model.elements.SlotElement;

/** Optional bridge for named machine storage and ghost slots. */
public interface InventoryAccess extends MenuComponent {
    int menuSlot(int storageIndex);

    int menuSlot(ItemInventories.Address address);

    boolean ghost(SlotElement slot, Player player, ItemStack stack);

    ItemStack hint(SlotElement slot);
}
