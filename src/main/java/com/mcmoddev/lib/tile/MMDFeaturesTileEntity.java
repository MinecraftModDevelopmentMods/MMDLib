package com.mcmoddev.lib.tile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import com.mcmoddev.lib.capability.CapabilitiesContainer;
import com.mcmoddev.lib.container.IWidgetContainer;
import com.mcmoddev.lib.container.gui.GuiContext;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.gui.layout.VerticalStackLayout;
import com.mcmoddev.lib.container.widget.IWidget;
import com.mcmoddev.lib.feature.FeatureDirtyLevel;
import com.mcmoddev.lib.feature.IFeature;
import com.mcmoddev.lib.feature.IFeatureHolder;
import com.mcmoddev.lib.feature.IServerFeature;
import com.mcmoddev.lib.util.LoggingUtil;
import com.mcmoddev.lib.util.NBTUtils;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class MMDFeaturesTileEntity extends MMDTileEntity implements IFeatureHolder, ITickable, IWidgetContainer {
    private final List<IFeature> features = new ArrayList<>();
    private final CapabilitiesContainer capContainer = new CapabilitiesContainer();
    private final Map<FeatureDirtyLevel, Set<String>> dirtyFeatures = new HashMap<>();

    protected MMDFeaturesTileEntity() {
        // TODO: move this out of constructor somehow
        this.initFeatures();
    }

    @Override
    public <T extends IFeature> T addFeature(final T feature) {
        this.features.add(feature);
        feature.setHolder(this);
        feature.initCapabilities(this.capContainer);
        this.featureChanged(feature, FeatureDirtyLevel.LOAD);
        return feature;
    }

    protected void initFeatures() { }

    @Override
    public final IFeature[] getFeatures() {
        return this.features.toArray(new IFeature[this.features.size()]);
    }

    @Override
    public void featureChanged(final IFeature feature, final FeatureDirtyLevel level) {
        this.dirtyFeatures.putIfAbsent(level, new HashSet<>());
        this.dirtyFeatures.get(level).add(feature.getKey());
        this.markDirty();
    }

    @Override
    public void update() {
        this.testForDirtyFeatures();
    }

    @SuppressWarnings("WeakerAccess")
    protected void testForDirtyFeatures() {
        if ((this.dirtyFeatures.size() > 0) && !this.getWorld().isRemote) {
            final NBTTagCompound nbt = this.getFeaturesUpdateTag(FeatureDirtyLevel.TICK,true);
            if (nbt.getSize() > 0) {
                LoggingUtil.logNbtMessage(this, "TICK UPDATE TAG", nbt);
                this.sendToListeningClients(nbt);
            }
        }
    }

    @Override
    public List<IWidget> getWidgets(final GuiContext context) {
        final List<IWidget> widgets = new ArrayList<>();

        for(final IFeature feature: this.features) {
            if (feature instanceof IWidgetContainer) {
                final IWidgetContainer container = (IWidgetContainer)feature;
                widgets.addAll(container.getWidgets(context));
            }
        }

        return widgets;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IWidgetGui getRootWidgetGui(final GuiContext context) {
        final VerticalStackLayout layout = new VerticalStackLayout();

        for (final IFeature feature : this.features) {
            if (feature instanceof IWidgetContainer) {
                final IWidgetContainer provider = (IWidgetContainer) feature;
                layout.addPiece(provider.getRootWidgetGui(context));
            }
        }

        return layout;
    }

    @Override
    public NBTTagCompound writeToNBT(final NBTTagCompound compound) {
        final NBTTagCompound featuresNBT = new NBTTagCompound();
        for(final IFeature feature: this.features) {
            final NBTTagCompound nbt = feature.serializeNBT();
            if (nbt != null) {
                featuresNBT.setTag(feature.getKey(), nbt);
            }
        }
        if (featuresNBT.getSize() > 0) {
            LoggingUtil.logNbtMessage(this, "WRITE FEATURES NBT", featuresNBT);
            compound.setTag("features", featuresNBT);
        }

        return super.writeToNBT(compound);
    }

    @Override
    public void readFromNBT(final NBTTagCompound compound) {
        // TODO: this is a hack... add an "internal" read features method and handle gui update tag correctly
        if (compound.getSize() >= 3) {
            super.readFromNBT(compound);
        }

        if (compound.hasKey("features", Constants.NBT.TAG_COMPOUND)) {
            final NBTTagCompound featuresNBT = compound.getCompoundTag("features");
            LoggingUtil.logNbtMessage(this, "READ FEATURES NBT", featuresNBT);
            for (final IFeature feature : this.features) {
                if (featuresNBT.hasKey(feature.getKey(), Constants.NBT.TAG_COMPOUND)) {
                    feature.deserializeNBT(featuresNBT.getCompoundTag(feature.getKey()));
                }
            }
        }
    }

    @Nullable
    @Override
    public NBTTagCompound getGuiUpdateTag(final boolean resetDirtyFlag) {
        return this.getFeaturesUpdateTag(FeatureDirtyLevel.GUI, resetDirtyFlag);
    }

    private NBTTagCompound getFeaturesUpdateTag(final FeatureDirtyLevel level, final boolean resetDirtyFlag) {
        final NBTTagCompound nbt = new NBTTagCompound();

        final Set<String> featureKeys = new HashSet<>();
        this.dirtyFeatures.forEach((key, value) -> {
            if (key.isMatchOrHigher(level)) {
                featureKeys.addAll(value);
            }
        });

        for(final IFeature feature: this.features) {
            if ((feature instanceof IServerFeature) && featureKeys.contains(feature.getKey())) {
                final NBTTagCompound updateTag = IServerFeature.class.cast(feature).getGuiUpdateTag(resetDirtyFlag);
                if (updateTag != null) {
                    nbt.setTag(feature.getKey(), updateTag);
                }
            }
        }
        this.dirtyFeatures.clear();

        return (nbt.getSize() > 0) ? NBTUtils.wrapCompound(nbt, "features") : nbt;
    }

    @Override
    public boolean hasCapability(final Capability<?> capability, @Nullable final EnumFacing facing) {
        return this.capContainer.hasCapability(capability, facing)
            || this.features.stream()
            .filter(f -> (f instanceof ICapabilityProvider))
            .anyMatch(f -> ((ICapabilityProvider)f).hasCapability(capability, facing))
            || super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(final Capability<T> capability, @Nullable final EnumFacing facing) {
        T cap = this.capContainer.getCapability(capability, facing);
        if (cap == null) {
            cap = this.features.stream()
                .filter(f -> (f instanceof ICapabilityProvider))
                .map(f -> ((ICapabilityProvider) f).getCapability(capability, facing))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        }
        return (cap != null) ? cap : super.getCapability(capability, facing);
    }
}
