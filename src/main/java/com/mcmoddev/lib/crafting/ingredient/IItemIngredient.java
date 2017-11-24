package com.mcmoddev.lib.crafting.ingredient;

import com.mcmoddev.lib.energy.EnergyUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

public interface IItemIngredient extends ICraftingIngredient, IFluidIngredientMatcher, IEnergyIngredientMatcher {
    NonNullList<ItemStack> getItemStacks();

    @Override
    default boolean isMatch(ICraftingIngredient ingredient, IngredientAmountMatch amountMatch) {
        if (ingredient instanceof IItemIngredient) {
            if (!amountMatch.compare(this, ingredient)) {
                // different amounts, don't even bother looking for a match
                return false;
            }
            // try to match one of this ingredient's stacks to one of the other ingredient's stacks
            NonNullList<ItemStack> stacks = ((IItemIngredient) ingredient).getItemStacks();
            for (ItemStack mine : this.getItemStacks()) {
                for (ItemStack other : stacks) {
                    if (mine.isItemEqualIgnoreDurability(other)) {
                        return true;
                    }
                }
            }
        }
        else if (ingredient instanceof IItemIngredientMatcher) {
            return ((IItemIngredientMatcher)ingredient).matchesItem(this, amountMatch);
        }
        return false;
    }

    @Override
    default boolean matchesFluid(IFluidIngredient ingredient, IngredientAmountMatch amountMatch) {
        FluidStack fluid = ingredient.getFluidStack();
        for(ItemStack stack: this.getItemStacks()) {
            FluidStack stackFluid = FluidUtil.getFluidContained(stack);
            if ((stackFluid != null) && stackFluid.isFluidEqual(fluid) && amountMatch.compare(stackFluid.amount, fluid.amount)) {
                return true;
            }
        }
        return false;
    }

    @Override
    default boolean matchesEnergy(IEnergyIngredient ingredient, IngredientAmountMatch amountMatch) {
        // TODO: make this more generic, currently only supports Forge Energy
        int energy = ingredient.getEnergy();
        for(ItemStack stack: getItemStacks()) {
            IEnergyStorage energyStorage = EnergyUtils.getEnergyStorage(stack);
            if ((energyStorage != null) && amountMatch.compare(energy, energyStorage.getEnergyStored())) {
                return true;
            }
        }
        return false;
    }
}
