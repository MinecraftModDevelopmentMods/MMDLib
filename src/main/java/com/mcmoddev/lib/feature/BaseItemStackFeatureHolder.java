package com.mcmoddev.lib.feature;

import java.util.List;
import com.google.common.collect.Lists;
import com.mcmoddev.lib.container.IContainerSlotsProvider;
import com.mcmoddev.lib.container.MMDContainer;
import com.mcmoddev.lib.gui.IGuiPiece;
import com.mcmoddev.lib.gui.IGuiPieceProvider;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

public class BaseItemStackFeatureHolder implements IFeatureHolder, IContainerSlotsProvider, IGuiPieceProvider {
    private final ItemStack stack;
    private List<IFeature> features = Lists.newArrayList();

    public BaseItemStackFeatureHolder(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public <T extends IFeature> T addFeature(T feature) {
        this.features.add(feature);
        feature.setHolder(this);

        NBTTagCompound storage = this.stack.getOrCreateSubCompound("features");
        if (storage.hasKey(feature.getKey(), Constants.NBT.TAG_COMPOUND)) {
            NBTTagCompound featureCompound = storage.getCompoundTag(feature.getKey());
            feature.deserializeNBT(featureCompound);
        }

        return feature;
    }

    @Override
    public IFeature[] getFeatures() {
        return this.features.toArray(new IFeature[this.features.size()]);
    }

    @Override
    public void featuredChanged(IFeature feature) {
        NBTTagCompound featureCompound = feature.serializeNBT();
        NBTTagCompound storage = this.stack.getOrCreateSubCompound("features");
        storage.setTag(feature.getKey(), featureCompound);
    }

    @Override
    public List<Slot> getContainerSlots(MMDContainer container) {
        List<Slot> slots = Lists.newArrayList();

        for(IFeature feature: this.features) {
            if (feature instanceof IContainerSlotsProvider) {
                IContainerSlotsProvider provider = (IContainerSlotsProvider)feature;
                slots.addAll(provider.getContainerSlots(container));
            }
        }

        return slots;
    }

    @Override
    public List<IGuiPiece> getPieces() {
        List<IGuiPiece> pieces = Lists.newArrayList();

        for(IFeature feature : this.features) {
            if (feature instanceof IGuiPieceProvider) {
                IGuiPieceProvider provider = (IGuiPieceProvider)feature;
                pieces.addAll(provider.getPieces());
            }
        }

        return pieces;
    }
}
