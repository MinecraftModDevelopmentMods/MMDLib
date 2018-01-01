package com.mcmoddev.lib.tile;

import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mcmoddev.lib.feature.IFeature;
import com.mcmoddev.lib.feature.IFeatureHolder;
import com.mcmoddev.lib.feature.IServerFeature;
import com.mcmoddev.lib.gui.GuiContext;
import com.mcmoddev.lib.gui.IGuiPiece;
import com.mcmoddev.lib.gui.IGuiPieceProvider;
import com.mcmoddev.lib.gui.layout.VerticalStackLayout;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ITickable;
import net.minecraftforge.common.util.Constants;

public class MMDFeaturesTileEntity extends MMDTileEntity implements IFeatureHolder, ITickable, /*IContainerSlotsProvider,*/ IGuiPieceProvider {
    private final List<IFeature> features = Lists.newArrayList();
    private final Set<String> dirtyFeatures = Sets.newHashSet();

    protected MMDFeaturesTileEntity() {
        // TODO: move this out of constructor somehow
        this.initFeatures();
    }

    @Override
    public <T extends IFeature> T addFeature(T feature) {
        this.features.add(feature);
        return feature;
    }

    protected void initFeatures() { }

    @Override
    public final IFeature[] getFeatures() {
        return this.features.toArray(new IFeature[this.features.size()]);
    }

    @Override
    public void featuredChanged(IFeature feature) {
        this.dirtyFeatures.add(feature.getKey());
        this.markDirty();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagCompound featuresNBT = new NBTTagCompound();
        for(IFeature feature: this.features) {
            NBTTagCompound nbt = feature.serializeNBT();
            if (nbt != null) {
                featuresNBT.setTag(feature.getKey(), nbt);
            }
        }
        if (featuresNBT.getSize() > 0) {
            compound.setTag("features", featuresNBT);
        }

        return super.writeToNBT(compound);
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);

        if (compound.hasKey("features", Constants.NBT.TAG_COMPOUND)) {
            NBTTagCompound featuresNBT = compound.getCompoundTag("features");
            for (IFeature feature : this.features) {
                if (featuresNBT.hasKey(feature.getKey(), Constants.NBT.TAG_COMPOUND)) {
                    feature.deserializeNBT(featuresNBT.getCompoundTag(feature.getKey()));
                }
            }
        }
    }

    @Nullable
    public NBTTagCompound getGuiUpdateTag(boolean resetDirtyFlag) {
        NBTTagCompound nbt = new NBTTagCompound();

        for(IFeature feature: this.features) {
            if ((feature instanceof IServerFeature) && this.dirtyFeatures.contains(feature.getKey())) {
                NBTTagCompound updateTag = IServerFeature.class.cast(feature).getGuiUpdateTag(resetDirtyFlag);
                if (updateTag != null) {
                    nbt.setTag(feature.getKey(), updateTag);
                }
            }
        }
        this.dirtyFeatures.clear();

        return nbt;
    }

    @Override
    public void update() {
        this.testForDirtyFeatures();
    }

    @SuppressWarnings("WeakerAccess")
    protected void testForDirtyFeatures() {
        if ((this.dirtyFeatures.size() > 0) && !this.getWorld().isRemote) {
            NBTTagCompound nbt = this.getGuiUpdateTag(true);
            if ((nbt != null) && (nbt.getSize() > 0)) {
                this.sendToListeningClients(nbt);
            }
        }
    }

//    @Override
//    public List<PlayerInventoryInfo> getPlayerSlots(MMDContainer container) {
//        List<PlayerInventoryInfo> inventories = super.getPlayerSlots(container);
//
//        for(IFeature feature : this.features) {
//            if (feature instanceof IPlayerInventoryProvider) {
//                IPlayerInventoryProvider provider = (IPlayerInventoryProvider)feature;
//                inventories.addAll(provider.getPlayerSlots(container));
//            }
//        }
//
//        return inventories;
//    }

//    @Override
//    public List<Slot> getContainerSlots(MMDContainer container) {
//        List<Slot> slots = Lists.newArrayList();
//
//        for(IFeature feature: this.features) {
//            if (feature instanceof IContainerSlotsProvider) {
//                IContainerSlotsProvider provider = (IContainerSlotsProvider)feature;
//                slots.addAll(provider.getContainerSlots(container));
//            }
//        }
//
//        return slots;
//    }

    @Override
    public IGuiPiece getRootPiece(GuiContext context) {
        VerticalStackLayout layout = new VerticalStackLayout();

        for (IFeature feature : this.features) {
            if (feature instanceof IGuiPieceProvider) {
                IGuiPieceProvider provider = (IGuiPieceProvider) feature;
                layout.addPiece(provider.getRootPiece(context));
            }
        }

        return layout;
    }
}
