package zone.moddev.mc.mmdlib.container.slot;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import zone.moddev.mc.mmdlib.inventory.IFilteredItemHandler;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * A {@link SlotItemHandler} that supports filtering.
 */
public class FilteredSlot extends SlotItemHandler {
    private final int index;
    private final IFilteredItemHandler filterInventory;

    /**
     * Initializes a new instance of FilteredSlot.
     * @param itemHandler The item handler this slot is for.
     * @param filterInventory The filtered item handler used to filter acceptable stacks for this slot.
     * @param index The index of the slot this slot is for.
     * @param xPosition The x coordinate of this slot.
     * @param yPosition The y coordinate of this slot.
     */
    public FilteredSlot(final IItemHandler itemHandler, @Nullable final IFilteredItemHandler filterInventory, final int index, final int xPosition, final int yPosition) {
        super(itemHandler, index, xPosition, yPosition);

        this.index = index;
        this.filterInventory = filterInventory;
    }

    @Override
    public boolean isItemValid(@Nonnull final ItemStack stack) {
        boolean isValid = true;
        if (this.filterInventory != null) {
            isValid = stack.isEmpty() || filterInventory.canInsertItem(this.index, stack);
        }
        return isValid && super.isItemValid(stack);
    }
}
