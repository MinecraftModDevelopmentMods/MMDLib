package com.mcmoddev.lib.gui;

import java.util.List;
import javax.annotation.Nullable;
import com.google.common.collect.Lists;
import com.mcmoddev.lib.container.IContainerSlotsProvider;
import com.mcmoddev.lib.container.IPlayerInventoryProvider;
import com.mcmoddev.lib.container.MMDContainer;
import com.mcmoddev.lib.container.PlayerInventoryInfo;
import com.mcmoddev.lib.feature.IClientFeature;
import com.mcmoddev.lib.feature.IFeature;
import com.mcmoddev.lib.feature.IFeatureHolder;
import com.mcmoddev.lib.feature.IItemStackFeatureHolder;
import com.mcmoddev.lib.feature.IServerFeature;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class BaseItemStackGuiProvider implements IGuiProvider, IGuiHolder, IContainerSlotsProvider, IGuiPieceProvider, IPlayerInventoryProvider {
    private final ItemStack stack;
    private final Item item;
    private final IFeatureHolder features;

    public BaseItemStackGuiProvider(ItemStack stack) {
        this(stack, stack.getItem());
    }

    public BaseItemStackGuiProvider(ItemStack stack, Item item) {
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
    public List<Slot> getContainerSlots(MMDContainer container) {
        List<Slot> slots = Lists.newArrayList();

        if (this.features != null) {
            for(IFeature feature: this.features.getFeatures()) {
                if (feature instanceof IContainerSlotsProvider) {
                    slots.addAll(IContainerSlotsProvider.class.cast(feature).getContainerSlots(container));
                }
            }
        }

        return slots;
    }

    @Override
    public List<IGuiPiece> getPieces() {
        List<IGuiPiece> pieces = Lists.newArrayList();

        if (this.features != null) {
            for(IFeature feature: this.features.getFeatures()) {
                if (feature instanceof IGuiPieceProvider) {
                    pieces.addAll(IGuiPieceProvider.class.cast(feature).getPieces());
                }
            }
        }

        return pieces;
    }

    @Override
    public List<PlayerInventoryInfo> getPlayerSlots(MMDContainer container) {
        List<PlayerInventoryInfo> inventories = Lists.newArrayList();

        if (this.features != null) {
            for(IFeature feature: this.features.getFeatures()) {
                if (feature instanceof IPlayerInventoryProvider) {
                    inventories.addAll(IPlayerInventoryProvider.class.cast(feature).getPlayerSlots(container));
                }
            }
        }

        return inventories;
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
                NBTTagCompound update = serverFeature.getUpdateTag(resetDirtyFlag);
                if ((update != null) && (update.getSize() > 0)) {
                    compound.setTag(feature.getKey(), update);
                }
            }
        }

        return (compound.getSize() > 0) ? compound : null;
    }
}
