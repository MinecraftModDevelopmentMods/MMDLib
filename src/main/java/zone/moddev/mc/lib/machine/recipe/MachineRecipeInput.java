package zone.moddev.mc.lib.machine.recipe;

import java.util.Objects;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

import zone.moddev.mc.lib.machine.inventory.ItemGroup;
import zone.moddev.mc.lib.machine.inventory.MachineInventory;

/**
 * A machine's inventory as a vanilla recipe input. Vanilla sees the item
 * groups' slots one after another, in group order; matching itself reads the
 * groups by key.
 */
public record MachineRecipeInput(MachineInventory inventory)
    implements RecipeInput {
    public MachineRecipeInput {
        Objects.requireNonNull(inventory, "inventory");
    }

    /**
     * A copy of the stack at {@code index} across the item groups; empty past
     * the end.
     */
    @Override
    public ItemStack getItem(int index) {
        int offset = index;
        for (var key : inventory.keys())
            if (inventory.group(key).orElseThrow() instanceof ItemGroup items) {
                if (offset < items.size())
                    return items.peek(offset);
                offset -= items.size();
            }
        return ItemStack.EMPTY;
    }

    /** The slot count across the item groups. */
    @Override
    public int size() {
        int size = 0;
        for (var key : inventory.keys())
            if (inventory.group(key).orElseThrow() instanceof ItemGroup items)
                size += items.size();
        return size;
    }

    /**
     * Never empty: a recipe can run on fluids or energy alone, and vanilla's
     * {@code getRecipeFor} returns nothing for an empty input without asking
     * the recipes.
     */
    @Override
    public boolean isEmpty() {
        return false;
    }
}
