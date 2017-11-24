package com.mcmoddev.lib.crafting.ingredient;

import com.mcmoddev.lib.energy.EnergyUtils;
import net.minecraft.item.ItemStack;
import net.minecraftforge.energy.IEnergyStorage;

public interface IEnergyIngredient extends ICraftingIngredient, IItemIngredientMatcher {
    // TODO: make this long?... long seems like something that might be needed for storage... not sure about ingredient.
    int getEnergy();

    @Override
    default boolean isMatch(ICraftingIngredient ingredient, IngredientAmountMatch amountMatch) {
        if (ingredient instanceof IEnergyIngredient) {
            // TODO: test for same/convertible energy system
            return amountMatch.compare(this, ingredient);
        }
        else if (ingredient instanceof IEnergyIngredientMatcher) {
            return ((IEnergyIngredientMatcher) ingredient).matchesEnergy(this, amountMatch);
        }
        return false;
    }

    @Override
    default boolean matchesItem(IItemIngredient ingredient, IngredientAmountMatch amountMatch) {
        for(ItemStack stack : ingredient.getItemStacks()) {
            IEnergyStorage energy = EnergyUtils.getEnergyStorage(stack);
            if (energy != null) {
                // TODO: allow testing of multiple energy systems
                if (amountMatch.compare(energy.getEnergyStored(), this.getEnergy())) {
                    return true;
                }
            }
        }
        return false;
    }
}
