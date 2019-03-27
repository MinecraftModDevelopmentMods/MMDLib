package com.mcmoddev.lib.container;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import com.mcmoddev.lib.container.gui.GuiContext;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.gui.MMDGuiContainer;
import com.mcmoddev.lib.container.gui.layout.VerticalStackLayout;
import com.mcmoddev.lib.container.widget.IWidget;
import com.mcmoddev.lib.feature.IClientFeature;
import com.mcmoddev.lib.feature.IFeature;
import com.mcmoddev.lib.feature.IFeatureHolder;
import com.mcmoddev.lib.feature.IItemStackFeatureHolder;
import com.mcmoddev.lib.feature.IServerFeature;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Base implementation of a gui provider for an item stack that has an item that implements {@link IItemStackFeatureHolder}.
 */
public class BaseItemStackWidgetProvider implements IGuiProvider, IWidgetContainer {
    private final ItemStack stack;
    private final Item item;
    private final IFeatureHolder features;

    /**
     * Initializes a new instance of BaseItemStackWidgetProvider.
     * @param stack The item stack this provider is for.
     * @implNote The corresponding item should implement {@link IItemStackFeatureHolder}.
     */
    public BaseItemStackWidgetProvider(final ItemStack stack) {
        this(stack, stack.getItem());
    }

    /**
     * Initializes a new instance of BaseItemStackWidgetProvider.
     * @param stack The item stack this provider is for.
     * @param item The item this provider is for. Usually same as {@link ItemStack#getItem()}.
     * @implNote The item should implement {@link IItemStackFeatureHolder}.
     */
    public BaseItemStackWidgetProvider(final ItemStack stack, final Item item) {
        this.stack = stack;
        this.item = item;
        this.features = (this.item instanceof IItemStackFeatureHolder)
            ? IItemStackFeatureHolder.class.cast(this.item).getFeatureHolder(this.stack)
            : (this.item instanceof IFeatureHolder)
            ? IFeatureHolder.class.cast(this.item)
            : null;
    }

    @Nullable
    @Override
    @SideOnly(Side.CLIENT)
    public GuiContainer getClientGui(final int id, final EntityPlayer player, final World world, final int x, final int y, final int z) {
        if (this.item instanceof IItemGuiProvider) {
            final IItemGuiProvider.GuiType type = (id == 1) ? IItemGuiProvider.GuiType.AIR : IItemGuiProvider.GuiType.BLOCK;
            return IItemGuiProvider.class.cast(this.item).getClientGui(type, this.stack, player, world, x, y, z);
        }

        final Container container = this.getServerGui(id, player, world, x, y, z);
        if (container instanceof MMDContainer) {
            return new MMDGuiContainer(this, player, MMDContainer.class.cast(container));
        }

        return null;
    }

    @Nullable
    @Override
    public Container getServerGui(final int id, final EntityPlayer player, final World world, final int x, final int y, final int z) {
        if (this.item instanceof IItemGuiProvider) {
            final IItemGuiProvider.GuiType type = (id == 1) ? IItemGuiProvider.GuiType.AIR : IItemGuiProvider.GuiType.BLOCK;
            return IItemGuiProvider.class.cast(this.item).getServerGui(type, this.stack, player, world, x, y, z);
        }
        return new MMDContainer(this, player);
    }

    @Override
    public List<IWidget> getWidgets(final GuiContext context) {
        final List<IWidget> widgets = new ArrayList<>();

        if (this.features != null) {
            for(final IFeature feature: this.features.getFeatures()) {
                if (feature instanceof IWidgetContainer) {
                    widgets.addAll(IWidgetContainer.class.cast(feature).getWidgets(context));
                }
            }
        }

        return widgets;
    }

    @Override
    public IWidgetGui getRootWidgetGui(final GuiContext context) {
        final VerticalStackLayout layout = new VerticalStackLayout();

        if (this.features != null) {
            for(final IFeature feature: this.features.getFeatures()) {
                if (feature instanceof IWidgetContainer) {
                    layout.addPiece(IWidgetContainer.class.cast(feature).getRootWidgetGui(context));
                }
            }
        }

        return layout;
    }

    @Override
    public void receiveGuiUpdateTag(final NBTTagCompound compound) {
        for(final IFeature feature : this.features.getFeatures()) {
            if ((feature instanceof IClientFeature) && compound.hasKey(feature.getKey(), Constants.NBT.TAG_COMPOUND)) {
                final IClientFeature clientFeature = IClientFeature.class.cast(feature);
                final NBTTagCompound update = compound.getCompoundTag(feature.getKey());
                clientFeature.handleUpdateTag(update);
            }
        }
    }

    @Nullable
    @Override
    public NBTTagCompound getGuiUpdateTag(final boolean resetDirtyFlag) {
        final NBTTagCompound compound = new NBTTagCompound();

        for(final IFeature feature : this.features.getFeatures()) {
            if (feature instanceof IServerFeature) {
                final IServerFeature serverFeature = IServerFeature.class.cast(feature);
                final NBTTagCompound update = serverFeature.getGuiUpdateTag(resetDirtyFlag);
                if ((update != null) && (update.getSize() > 0)) {
                    compound.setTag(feature.getKey(), update);
                }
            }
        }

        return (compound.getSize() > 0) ? compound : null;
    }
}
