package com.mcmoddev.lib.crafting.ingredient;

import com.mcmoddev.lib.crafting.ingredient.implementation.FluidIngredient;
import com.mcmoddev.lib.crafting.ingredient.implementation.ItemIngredient;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public class IngredientUtils {
    public static IItemIngredient wrapItemStack(ItemStack stack) {
        return new ItemIngredient(stack);
    }

    public static IFluidIngredient wrapFluidStack(FluidStack stack) {
        return new FluidIngredient(stack);
    }
}
