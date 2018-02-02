package com.mcmoddev.lib.feature;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import com.google.common.collect.Lists;
import com.mcmoddev.lib.container.IWidgetContainer;
import com.mcmoddev.lib.container.gui.GuiContext;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.gui.layout.VerticalStackLayout;
import com.mcmoddev.lib.container.widget.IWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class BaseItemStackFeatureHolder implements IFeatureHolder, IWidgetContainer {
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

    private Stream<IWidget> getWidgetsStream(GuiContext context) {
        return this.features
            .stream()
            .map(f -> (f instanceof IWidgetContainer) ? IWidgetContainer.class.cast(f) : null)
            .filter(Objects::nonNull)
            .flatMap(c -> c.getWidgets(context).stream());
    }

    @Override
    public List<IWidget> getWidgets(GuiContext context) {
        return this.getWidgetsStream(context)
            .collect(Collectors.toList());
    }

    @Nullable
    @Override
    public NBTTagCompound getGuiUpdateTag(boolean resetDirtyFlag) {
        // Widgets are handled by the container, not this class
        NBTTagCompound nbt = new NBTTagCompound();
//        this.getWidgetsStream().forEach(widget -> {
//            if (widget.isDirty()) {
//                NBTTagCompound updateTag = widget.getUpdateCompound();
//                if (updateTag != null) {
//                    NBTUtils.setInnerCompound(nbt, updateTag, widget.getKey());
//                }
//            }
//        });
        return (nbt.getSize() > 0) ? nbt : null;
    }

    @Nullable
    @Override
    @SideOnly(Side.CLIENT)
    public IMessage receiveGuiUpdateTag(NBTTagCompound compound) {
        // Widgets are handled by the container, not this class
//        this.getWidgetsStream().forEach(widget -> {
//            if (compound.hasKey(widget.getKey(), Constants.NBT.TAG_COMPOUND)) {
//                widget.handleMessageFromServer(compound.getCompoundTag(widget.getKey()));
//            }
//        });
        return null;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IWidgetGui getRootWidgetGui(GuiContext context) {
        VerticalStackLayout layout = new VerticalStackLayout();

        for(IFeature feature : this.features) {
            if (feature instanceof IWidgetContainer) {
                IWidgetContainer provider = (IWidgetContainer)feature;
                layout.addPiece(provider.getRootWidgetGui(context));
            }
        }

        return layout;
    }
}
