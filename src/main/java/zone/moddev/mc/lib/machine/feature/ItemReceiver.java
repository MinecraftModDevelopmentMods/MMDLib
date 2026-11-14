package zone.moddev.mc.lib.machine.feature;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * A right-click destination that owns its acceptance rules, independent of the
 * offered item.
 */
public interface ItemReceiver {
    /**
     * Pure recognition on either side; return true even when temporarily full
     * or unavailable.
     */
    boolean accepts(ItemStack offered);

    /**
     * Pure offer sizing with a detached held stack. Defaults to one item for
     * container exchanges; return held.getCount() to receive the whole stack.
     * The router clamps this limit between one and the held count.
     */
    default int offerLimit(ItemStack held) {
        return 1;
    }

    /**
     * Server only. Receives a detached copy sized by offerLimit. Reject without
     * changing anything, or commit acceptance (possibly partial) and return all
     * unaccepted items or the resulting container. Never mutate the player's
     * hand.
     */
    Exchange receive(ItemStack offered, UseOnContext context);

    record Exchange(boolean accepted, ItemStack remainder,
        boolean consumeInCreative) {
        public Exchange {
            remainder = remainder.copy();
            if (!accepted && !remainder.isEmpty())
                throw new IllegalArgumentException(
                    "Rejected exchanges cannot return items");
        }
        @Override
        public ItemStack remainder() {
            return remainder.copy();
        }
        public static Exchange reject() {
            return new Exchange(false, ItemStack.EMPTY, true);
        }
        public static Exchange accept(ItemStack remainder) {
            return new Exchange(true, remainder, true);
        }
        /**
         * Vanilla-style renewable containers retain their held item in creative
         * mode.
         */
        public static Exchange reusable(ItemStack remainder) {
            return new Exchange(true, remainder, false);
        }
    }
}
