package com.mcmoddev.lib.crafting.extractor.implementation;

import java.util.stream.Stream;
import javax.annotation.Nullable;
import com.mcmoddev.lib.crafting.ingredient.ICraftingIngredient;
import com.mcmoddev.lib.crafting.ingredient.IngredientUtils;
import com.mcmoddev.lib.crafting.input.ICraftingInput;
import com.mcmoddev.lib.crafting.input.ICraftingItemInput;
import com.mcmoddev.lib.crafting.inventory.ICraftingInventory;
import com.mcmoddev.lib.crafting.inventory.IItemInventory;
import com.mcmoddev.lib.util.ItemStackUtils;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

public class ItemExtractor extends BaseCraftingExtractor {
    public static final ItemExtractor INSTANCE = new ItemExtractor("default_item_extractor");

    protected ItemExtractor(String key) {
        super(key);
    }

    @Nullable
    @Override
    public ICraftingIngredient extract(ICraftingInput input, ICraftingInventory inventory, boolean simulate) {
        ICraftingItemInput itemInput = (input instanceof ICraftingItemInput) ? (ICraftingItemInput)input : null;
        IItemInventory itemInventory = (inventory instanceof IItemInventory) ? (IItemInventory)inventory : null;
        if ((itemInput != null) && (itemInventory != null)) {
            Stream<ItemStack> inputStacks = itemInput.getPossibleInputs().stream();
            int extracted = 0;
            ItemStack result = ItemStack.EMPTY;
            for (int i = 0; i < itemInventory.getSlots(); i++) {
                ItemStack stack = itemInventory.getSlotContent(i);
                if (inputStacks.anyMatch(s -> ItemStackUtils.areEqualIgnoreSize(s, stack))) {
                    int toExtract = Math.min(stack.getCount(), (input.getAmount() - extracted));
                    if ((toExtract > 0) && ((result.isEmpty() || ItemHandlerHelper.canItemStacksStack(result, stack)))) {
                        if (result.isEmpty()) {
                            result = ItemStackUtils.copyWithSize(stack, toExtract);
                        }
                        else {
                            result.grow(toExtract);
                        }
                        extracted += toExtract;
                        if (!simulate) {
                            stack.shrink(toExtract);
                        }

                        if (result.getCount() == input.getAmount()) {
                            return IngredientUtils.wrapItemStack(result);
                        }
                    }
                }
            }
        }
        return null;
    }
}
