package zone.moddev.mc.lib.gui.runtime.component;

import java.util.List;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.gui.layout.ScreenLayout;
import zone.moddev.mc.lib.gui.runtime.slot.MenuSlots;

/** Optional native-slot assembly, placement and shift-click routing. */
public interface MenuSlotContributor extends MenuComponent {
    void addSlots(MenuSlots.Registrar slots, ScreenLayout layout);

    default void finishSlots(MenuSlots.Registrar slots, ScreenLayout layout) {
    }

    default void position(ScreenLayout layout) {
    }

    default int transferPriority() {
        return 0;
    }

    default List<Slot> destinations(ItemStack stack) {
        return List.of();
    }
}
