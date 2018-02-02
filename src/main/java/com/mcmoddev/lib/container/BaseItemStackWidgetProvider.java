package com.mcmoddev.lib.container;

import javax.annotation.Nullable;
import com.mcmoddev.lib.container.gui.GuiContext;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.gui.MMDGuiContainer;
import com.mcmoddev.lib.container.gui.layout.VerticalStackLayout;
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
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class BaseItemStackWidgetProvider implements IGuiProvider, IWidgetContainer {
    private final ItemStack stack;
    private final Item item;
    private final IFeatureHolder features;

    public BaseItemStackWidgetProvider(ItemStack stack) {
        this(stack, stack.getItem());
    }

    public BaseItemStackWidgetProvider(ItemStack stack, Item item) {
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
    public GuiContainer getClientGui(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (this.item instanceof IItemGuiProvider) {
            IItemGuiProvider.GuiType type = (id == 1) ? IItemGuiProvider.GuiType.AIR : IItemGuiProvider.GuiType.BLOCK;
            return IItemGuiProvider.class.cast(this.item).getClientGui(type, this.stack, player, world, x, y, z);
        }

        Container container = this.getServerGui(id, player, world, x, y, z);
        if (container instanceof MMDContainer) {
            return new MMDGuiContainer(this, player, MMDContainer.class.cast(container));
        }

        return null;
    }

    @Nullable
    @Override
    public Container getServerGui(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (this.item instanceof IItemGuiProvider) {
            IItemGuiProvider.GuiType type = (id == 1) ? IItemGuiProvider.GuiType.AIR : IItemGuiProvider.GuiType.BLOCK;
            return IItemGuiProvider.class.cast(this.item).getServerGui(type, this.stack, player, world, x, y, z);
        }
        return new MMDContainer(this, player);
    }


    @Override
    public IWidgetGui getRootWidgetGui(GuiContext context) {
        VerticalStackLayout layout = new VerticalStackLayout();

        if (this.features != null) {
            for(IFeature feature: this.features.getFeatures()) {
                if (feature instanceof IWidgetContainer) {
                    layout.addPiece(IWidgetContainer.class.cast(feature).getRootWidgetGui(context));
                }
            }
        }

        return layout;
    }

    @Nullable
    @Override
    public IMessage receiveGuiUpdateTag(NBTTagCompound compound) {
        for(IFeature feature : this.features.getFeatures()) {
            if ((feature instanceof IClientFeature) && compound.hasKey(feature.getKey(), Constants.NBT.TAG_COMPOUND)) {
                IClientFeature clientFeature = IClientFeature.class.cast(feature);
                NBTTagCompound update = compound.getCompoundTag(feature.getKey());
                clientFeature.handleUpdateTag(update);
            }
        }

        return null;
    }

    @Nullable
    @Override
    public NBTTagCompound getGuiUpdateTag(boolean resetDirtyFlag) {
        NBTTagCompound compound = new NBTTagCompound();

        for(IFeature feature : this.features.getFeatures()) {
            if (feature instanceof IServerFeature) {
                IServerFeature serverFeature = IServerFeature.class.cast(feature);
                NBTTagCompound update = serverFeature.getGuiUpdateTag(resetDirtyFlag);
                if ((update != null) && (update.getSize() > 0)) {
                    compound.setTag(feature.getKey(), update);
                }
            }
        }

        return (compound.getSize() > 0) ? compound : null;
    }
}
