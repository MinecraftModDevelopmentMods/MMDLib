package zone.moddev.mc.lib.gui.runtime.slot;

import java.util.Optional;
import java.util.function.Predicate;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.machine.inventory.SlotPlan;

/** Optional behavior implemented by native slots, including mod-provided slot classes. */
public interface SlotAdapter {
    default void endInteraction() {
    }

    default SlotPlan plan() {
        return SlotPlan.NONE;
    }

    default void applyPlan(SlotPlan plan) {
    }

    /**
     * Empty delegates to vanilla-style transfer; a present empty stack means handled without
     * moving.
     */
    default Optional<ItemStack> quickMove(Player player, Predicate<ItemStack> move) {
        return Optional.empty();
    }
}
