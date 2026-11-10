package zone.moddev.mc.lib.gui.model.elements;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.SlotItemHandler;

import zone.moddev.mc.lib.api.capabilities.ItemStore;
import zone.moddev.mc.lib.api.capabilities.ReservationReceiver;
import zone.moddev.mc.lib.api.capabilities.ReservedSlots;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.SlotKind;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;

/** A real item slot, owning its vanilla adapter and reservation-aware player interaction. */
public class ItemSlotElement implements SlotElement {
    private final Common common;
    private final int index;
    private final SlotKind kind;
    private final Appearance appearance;

    public ItemSlotElement(Common common, int index, SlotKind kind, Appearance appearance) {
        Objects.requireNonNull(common);
        Objects.requireNonNull(appearance);
        Objects.requireNonNull(kind);
        if (kind == SlotKind.GHOST)
            throw new IllegalArgumentException("Use GhostSlotElement");

        this.common = common;
        this.index = index;
        this.kind = kind;
        this.appearance = appearance;
    }

    public MenuSlot createSlot(
        IItemHandlerModifiable storage,
        int local,
        Rect bounds,
        BooleanSupplier visible,
        BooleanSupplier interacting,
        Runnable changed) {
        var xy = itemPosition(bounds);
        return new MenuSlot(
            storage, local, xy[0], xy[1], visible, interacting, changed, kind == SlotKind.UPGRADE);
    }

    /** Per-menu state; element definitions never hold a player's mutable interaction state. */
    public static class MenuSlot extends SlotItemHandler
        implements
            zone.moddev.mc.lib.gui.runtime.slot.SlotAdapter {
        private final BooleanSupplier visible, interacting;
        private final Runnable changed;
        private final boolean upgrade;
        private ItemStack pending;
        private final int local;

        protected MenuSlot(
            IItemHandlerModifiable storage,
            int index,
            int x,
            int y,
            BooleanSupplier visible,
            BooleanSupplier interacting,
            Runnable changed,
            boolean upgrade) {
            super(storage, index, x, y);
            this.local = index;
            this.visible = visible;
            this.interacting = interacting;
            this.changed = changed;
            this.upgrade = upgrade;
        }

        public boolean isActive() {
            return visible.getAsBoolean();
        }

        public boolean mayPlace(ItemStack stack) {
            return isActive() && super.mayPlace(stack);
        }

        public boolean mayPickup(Player player) {
            return isActive() && (!plan().isNone() || super.mayPickup(player));
        }

        public int getMaxStackSize() {
            return upgrade ? 1 : super.getMaxStackSize();
        }

        public SlotPlan plan() {
            return getItemHandler() instanceof ReservedSlots reserved
                ? reserved.plan(local)
                : SlotPlan.NONE;
        }

        public void applyPlan(SlotPlan plan) {
            if (getItemHandler() instanceof ReservationReceiver client)
                client.setPlan(local, plan);
        }

        private ReservedSlots reservation() {
            return interacting.getAsBoolean()
                && getItemHandler() instanceof ReservedSlots reserved
                && reserved.hasPlan(local)
                    ? reserved
                    : null;
        }

        private int locked(ReservedSlots reserved) {
            return Math.max(0, super.getItem().getCount() - reserved.freeToTake(local));
        }

        public ItemStack getItem() {
            var reserved = reservation();
            return reserved == null
                ? super.getItem()
                : (pending = super.getItem().copyWithCount(reserved.freeToTake(local)));
        }

        public int getMaxStackSize(ItemStack stack) {
            if (!(getItemHandler() instanceof ReservedSlots reserved) || !reserved.hasPlan(local))
                return Math.min(getMaxStackSize(), super.getMaxStackSize(stack));
            int limit = Math.min(getMaxStackSize(), reserved.maxHeld(local, stack));
            return Math.max(0, limit - (interacting.getAsBoolean() ? locked(reserved) : 0));
        }

        public void set(ItemStack stack) {
            var reserved = reservation();
            var result = stack;
            if (reserved != null) {
                int locked = locked(reserved);
                if (locked > 0) {
                    var held = super.getItem();
                    if (!stack.isEmpty() && !ItemStack.isSameItemSameComponents(held, stack))
                        throw new IllegalArgumentException("Cannot replace reserved items");
                    result = held.copyWithCount(locked + stack.getCount());
                }
            }
            pending = null;
            ((IItemHandlerModifiable) getItemHandler()).setStackInSlot(local, result);
            setChanged();
        }

        public void setChanged() {
            if (pending != null && reservation() != null) {
                var update = pending;
                pending = null;
                set(update);
            } else {
                super.setChanged();
                if (getItemHandler() instanceof ItemStore storage)
                    storage.markChanged();
                changed.run();
            }
        }

        public ItemStack remove(int amount) {
            pending = null;
            return super.remove(amount);
        }

        public java.util.Optional<ItemStack> quickMove(Player player, Predicate<ItemStack> move) {
            return plan().isNone()
                ? java.util.Optional.empty()
                : java.util.Optional.of(transferAvailable(player, move));
        }

        public void endInteraction() {
            pending = null;
        }

        /**
         * Shift-click only the extractable portion, then remove exactly what the destination
         * accepted.
         */
        public ItemStack transferAvailable(Player player, Predicate<ItemStack> move) {
            int free = getItemHandler().extractItem(local, plan().free(super.getItem()), true)
                .getCount();
            if (free <= 0)
                return ItemStack.EMPTY;
            var moving = super.getItem().copyWithCount(free);
            var original = moving.copy();
            if (!move.test(moving))
                return ItemStack.EMPTY;
            int moved = free - moving.getCount();
            var taken = remove(moved);
            onTake(player, taken);
            return original.copyWithCount(moved);
        }
    }

    public Common common() {
        return common;
    }

    public int index() {
        return index;
    }

    public SlotKind kind() {
        return kind;
    }

    public Appearance appearance() {
        return appearance;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (object == null || getClass() != object.getClass())
            return false;
        var other = (ItemSlotElement) object;
        return java.util.Objects.equals(common, other.common)
            && java.util.Objects.equals(index, other.index)
            && java.util.Objects.equals(kind, other.kind)
            && java.util.Objects.equals(appearance, other.appearance);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(common);
        hash = 31 * hash + java.util.Objects.hashCode(index);
        hash = 31 * hash + java.util.Objects.hashCode(kind);
        hash = 31 * hash + java.util.Objects.hashCode(appearance);
        return hash;
    }

    @Override
    public String toString() {
        return "ItemSlotElement[common="
            + common
            + ", index="
            + index
            + ", kind="
            + kind
            + ", appearance="
            + appearance
            + "]";
    }
}
