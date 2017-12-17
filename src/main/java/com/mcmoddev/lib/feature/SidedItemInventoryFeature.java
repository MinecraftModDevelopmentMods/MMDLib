package com.mcmoddev.lib.feature;

import java.util.Arrays;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.IItemHandlerModifiable;

public class SidedItemInventoryFeature extends ItemInventoryFeature implements ISidedFeature {
    private EnumFacing[] facings = new EnumFacing[0];

    public SidedItemInventoryFeature(String key, int slots,
                                     @Nullable BiPredicate<Integer, ItemStack> insertFilter,
                                     @Nullable BiPredicate<Integer, ItemStack> extractFilter,
                                     @Nullable Function<Integer, ItemStack[]> validStacksGetter) {
        super(key, slots, insertFilter, extractFilter, validStacksGetter);
    }

    public SidedItemInventoryFeature(String key, IItemHandlerModifiable handler,
                                     @Nullable BiPredicate<Integer, ItemStack> insertFilter,
                                     @Nullable BiPredicate<Integer, ItemStack> extractFilter,
                                     @Nullable Function<Integer, ItemStack[]> validStacksGetter) {
        super(key, handler, insertFilter, extractFilter, validStacksGetter);
    }

    @Override
    public EnumFacing[] getFacings() {
        return this.facings;
    }

    @Override
    public void setFacings(EnumFacing[] facings) {
        this.facings = facings;
        this.setDirty();
    }

    @Override
    protected void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);

        if ((this.facings != null) && (this.facings.length > 0)) {
            serializeNBT().setTag("facings", new NBTTagIntArray(
                Arrays
                    .stream(this.facings)
                    .map(EnumFacing::getIndex)
                    .collect(Collectors.toList())
            ));
        }
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        super.deserializeNBT(nbt);

        if (nbt.hasKey("facings", Constants.NBT.TAG_INT_ARRAY)) {
            this.facings = Arrays
                .stream(nbt.getIntArray("facings"))
                .mapToObj(EnumFacing::getFront)
                .toArray(EnumFacing[]::new);
        }
        else {
            this.facings = new EnumFacing[0];
        }
    }
}
