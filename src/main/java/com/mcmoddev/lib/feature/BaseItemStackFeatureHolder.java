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
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Basic implementation of a {@link IFeatureHolder feature holder} that works with item stacks.
 */
@SuppressWarnings("WeakerAccess")
public class BaseItemStackFeatureHolder implements IFeatureHolder, IWidgetContainer {
    private final ItemStack stack;
    private final List<IFeature> features = Lists.newArrayList();

    /**
     * Initializes a new instance of BaseItemStackFeatureHolder.
     * @param stack The item stack to get features from and to store features data in.
     */
    public BaseItemStackFeatureHolder(final ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public <T extends IFeature> T addFeature(final T feature) {
        this.features.add(feature);
        feature.setHolder(this);

        final NBTTagCompound storage = this.stack.getOrCreateSubCompound("features");
        if (storage.hasKey(feature.getKey(), Constants.NBT.TAG_COMPOUND)) {
            final NBTTagCompound featureCompound = storage.getCompoundTag(feature.getKey());
            feature.deserializeNBT(featureCompound);
        }

        return feature;
    }

    @Override
    public IFeature[] getFeatures() {
        return this.features.toArray(new IFeature[this.features.size()]);
    }

    @Override
    public void featureChanged(final IFeature feature, final FeatureDirtyLevel level) {
        final NBTTagCompound featureCompound = feature.serializeNBT();
        final NBTTagCompound storage = this.stack.getOrCreateSubCompound("features");
        storage.setTag(feature.getKey(), featureCompound);
    }

    private Stream<IWidget> getWidgetsStream(final GuiContext context) {
        return this.features
            .stream()
            .map(f -> (f instanceof IWidgetContainer) ? IWidgetContainer.class.cast(f) : null)
            .filter(Objects::nonNull)
            .flatMap(c -> c.getWidgets(context).stream());
    }

    @Override
    public List<IWidget> getWidgets(final GuiContext context) {
        return this.getWidgetsStream(context)
            .collect(Collectors.toList());
    }

    @Nullable
    @Override
    public NBTTagCompound getGuiUpdateTag(final boolean resetDirtyFlag) {
        final NBTTagCompound nbt = new NBTTagCompound();
        return (nbt.getSize() > 0) ? nbt : null;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void receiveGuiUpdateTag(final NBTTagCompound compound) {
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IWidgetGui getRootWidgetGui(final GuiContext context) {
        final VerticalStackLayout layout = new VerticalStackLayout();

        for(final IFeature feature : this.features) {
            if (feature instanceof IWidgetContainer) {
                final IWidgetContainer provider = (IWidgetContainer)feature;
                layout.addPiece(provider.getRootWidgetGui(context));
            }
        }

        return layout;
    }
}
