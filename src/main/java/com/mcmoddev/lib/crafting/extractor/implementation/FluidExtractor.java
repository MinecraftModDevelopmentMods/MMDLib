package com.mcmoddev.lib.crafting.extractor.implementation;

import java.util.stream.Stream;
import javax.annotation.Nullable;
import com.mcmoddev.lib.crafting.ingredient.ICraftingIngredient;
import com.mcmoddev.lib.crafting.ingredient.IngredientUtils;
import com.mcmoddev.lib.crafting.input.ICraftingFluidInput;
import com.mcmoddev.lib.crafting.input.ICraftingInput;
import com.mcmoddev.lib.crafting.input.ICraftingItemInput;
import com.mcmoddev.lib.crafting.inventory.ICraftingInventory;
import com.mcmoddev.lib.crafting.inventory.IFluidInventory;
import com.mcmoddev.lib.crafting.inventory.IItemInventory;
import com.mcmoddev.lib.util.FluidStackUtils;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

public class FluidExtractor extends BaseCraftingExtractor {
    public static final FluidExtractor INSTANCE = new FluidExtractor("default_fluid_extractor");

    protected FluidExtractor(String key) {
        super(key);
    }

    @Nullable
    @Override
    public ICraftingIngredient extract(ICraftingInput input, ICraftingInventory inventory, boolean simulate) {
        ICraftingFluidInput fluidInput = (input instanceof ICraftingItemInput) ? (ICraftingFluidInput)input : null;
        if (fluidInput != null) {
            Stream<FluidStack> expected = fluidInput.getPossibleInputs().stream();
            int extracted = 0;
            FluidStack result = null;

            if (inventory instanceof IFluidInventory) {
                IFluidInventory fluidInventory = (IFluidInventory)inventory;
                for (int i = 0; i < fluidInventory.getSlots(); i++) {
                    FluidStack existing = fluidInventory.getSlotContent(i);
                    if ((existing != null) && expected.anyMatch(f -> f.isFluidEqual(existing))) {
                        int toExtract = Math.min(existing.amount, (input.getAmount() - extracted));
                        if ((toExtract > 0) && ((result == null) || result.isFluidEqual(existing))) {
                            if (result == null) {
                                result = FluidStackUtils.copyWithSize(existing, toExtract);
                            } else {
                                result.amount += toExtract;
                            }

                            if (!simulate) {
                                fluidInventory.setSlotContent(i, FluidStackUtils.copyWithSize(existing, existing.amount - toExtract));
                            }

                            if ((result != null) && (result.amount == input.getAmount())) {
                                return IngredientUtils.wrapFluidStack(result);
                            }
                        }
                    }
                }
            }
            else if (inventory instanceof IItemInventory) {
                IItemInventory itemInventory = (IItemInventory) inventory;
                for (int i = 0; i < itemInventory.getSlots(); i++) {
                    ItemStack stack = itemInventory.getSlotContent(i);
                    IFluidHandlerItem handler = stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
                    if (handler != null) {
                        FluidStack contained = FluidUtil.getFluidContained(stack);
                        if ((contained != null) && expected.anyMatch(f -> f.isFluidEqual(contained))) {
                            int toExtract = Math.min(contained.amount, (input.getAmount() - extracted));
                            if ((toExtract > 0) && ((result == null) || result.isFluidEqual(contained))) {
                                if (result == null) {
                                    result = FluidStackUtils.copyWithSize(contained, toExtract);
                                } else {
                                    result.amount += toExtract;
                                }
                                extracted += toExtract;
                                if (!simulate) {
                                    // TODO: maybe add a test to see if it succeeded or not
                                    handler.drain(FluidStackUtils.copyWithSize(contained, toExtract), true);
                                    itemInventory.setSlotContent(i, handler.getContainer());
                                }

                                if ((result != null) && (result.amount == input.getAmount())) {
                                    return IngredientUtils.wrapFluidStack(result);
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
}
