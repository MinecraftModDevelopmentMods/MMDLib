package zone.moddev.mc.lib.compat.curios;

import java.util.List;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.gui.runtime.EquippedContainers.Source;

/**
 * Loaded only after the Curios mod-presence check. Cosmetic slots are
 * intentionally excluded.
 */
public final class CuriosContainers {
    private CuriosContainers() {
    }

    public static List<Source> find(Player player) {
        return CuriosEquipment.discover(player, ItemStack.EMPTY).containers();
    }
}
