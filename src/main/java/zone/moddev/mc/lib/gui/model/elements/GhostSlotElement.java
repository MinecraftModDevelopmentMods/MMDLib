package zone.moddev.mc.lib.gui.model.elements;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import zone.moddev.mc.lib.gui.ElementAction;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.SlotKind;
import zone.moddev.mc.lib.gui.runtime.component.InventoryAccess;

/** A filter selection: it renders a ghost item and never moves real items. */
public class GhostSlotElement implements SlotElement {
    private final Common common;
    private final int index;
    private final Appearance appearance;

    /**
     * Shared by ordinary ghost clicks and ingredient drops; transport
     * authorizes the context.
     */
    public static final ElementAction<ItemStack> SELECT_GHOST = new ElementAction<>(
        net.minecraft.resources.ResourceLocation
            .parse("mmdlib:ghost_selection"),
        net.minecraft.network.codec.StreamCodec.of(
            (buffer, stack) -> ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer,
                stack),
            buffer -> ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer)),
        4096,
        (context, stack) -> context.element() instanceof SlotElement slot
            && slot.ghost()
            && context.menu().components()
                .requireCapability(InventoryAccess.class)
                .ghost(slot, context.player(), stack));

    public GhostSlotElement(Common common, int index, Appearance appearance) {
        Objects.requireNonNull(common);
        Objects.requireNonNull(appearance);

        this.common = common;
        this.index = index;
        this.appearance = appearance;
    }

    public SlotKind kind() {
        return SlotKind.GHOST;
    }

    @Override
    public Slot createSlot(
        net.neoforged.neoforge.items.IItemHandlerModifiable storage, int local,
        Rect bounds, BooleanSupplier visible, BooleanSupplier interacting,
        Runnable changed) {
        return createSlot(bounds, visible);
    }

    public Slot createSlot(Rect bounds, BooleanSupplier visible) {
        int[] xy = itemPosition(bounds);
        return new Slot(new SimpleContainer(1), 0, xy[0], xy[1]) {
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            public boolean mayPickup(Player player) {
                return false;
            }

            public boolean isActive() {
                return visible.getAsBoolean();
            }
        };
    }

    public Common common() {
        return common;
    }

    public int index() {
        return index;
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
        var other = (GhostSlotElement) object;
        return java.util.Objects.equals(common, other.common)
            && java.util.Objects.equals(index, other.index)
            && java.util.Objects.equals(appearance, other.appearance);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash = 31 * hash + java.util.Objects.hashCode(common);
        hash = 31 * hash + java.util.Objects.hashCode(index);
        hash = 31 * hash + java.util.Objects.hashCode(appearance);
        return hash;
    }

    @Override
    public String toString() {
        return "GhostSlotElement[common=" + common + ", index=" + index
            + ", appearance=" + appearance + "]";
    }
}
