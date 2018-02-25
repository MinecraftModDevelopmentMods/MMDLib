package com.mcmoddev.lib.container;

import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import com.google.common.collect.Lists;
import com.mcmoddev.lib.MMDLib;
import com.mcmoddev.lib.container.gui.GuiContext;
import com.mcmoddev.lib.container.widget.IContextualWidget;
import com.mcmoddev.lib.container.widget.IProxyWidget;
import com.mcmoddev.lib.container.widget.IWidget;
import com.mcmoddev.lib.network.MMDPackages;
import com.mcmoddev.lib.network.NBTBasedPlayerMessage;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

@SuppressWarnings("WeakerAccess")
public class MMDContainer extends Container {
    private final static double MAX_INTERACT_DISTANCE = 64.0;

    private final IWidgetContainer provider;
    private final EntityPlayer player;
    private final List<IWidget> widgets;

    public MMDContainer(final IWidgetContainer provider, final EntityPlayer player) {
        super();

        this.provider = provider;
        this.player = player;

        final GuiContext context = new GuiContext(player, this, provider);
        this.widgets = this.provider
            .getWidgets(context)
            .stream()
            .map(w -> (w instanceof IProxyWidget) ? ((IProxyWidget)w).getContextualWidget(context) : w)
            .collect(Collectors.toList());
        final List<IContainerSlot> slots = Lists.newArrayList();
        for(final IWidget widget : this.widgets) {
            if (widget instanceof IContextualWidget) {
                ((IContextualWidget)widget).setContext(context);
            }
            slots.addAll(widget.getSlots());
        }

        MMDLib.logger.info("MMD CONTAINER created with " + slots.size() + " slots.");
        for(final IContainerSlot slot: slots) {
            final Slot realSlot = slot.getSlot();
            this.addSlotToContainer(realSlot);
            slot.setIndex(realSlot.slotNumber);
        }
    }

    public IWidgetContainer getProvider() {
        return this.provider;
    }

    @Override
    public boolean canInteractWith(final EntityPlayer playerIn) {
        return this.provider.isValid() && (this.provider.getDistance(playerIn) <= MAX_INTERACT_DISTANCE);
    }

    @Override
    public ItemStack transferStackInSlot(final EntityPlayer playerIn, final int index) {
        final ItemStack copyStack = ItemStack.EMPTY;
//        Slot slot = this.inventorySlots.get(index);
//
//        if ((slot != null) && slot.getHasStack()) {
//            ItemStack origStack = slot.getStack();
//            copyStack = origStack.copy();
//
//            boolean merged = false;
//            for (SlotRange range : this.getSlotsRange(index)) {
//                if (super.mergeItemStack(origStack, range.start, range.end, range.reverse)) {
//                    merged = true;
//                    break;
//                }
//            }
//            if (!merged) {
//                return ItemStack.EMPTY;
//            }
//        }

        return copyStack;
    }

//    private List<SlotRange> getSlotsRange(int sourceIndex) {
//        List<SlotRange> list = Lists.newArrayList();
//
//        if (sourceIndex >= this.entitySlots) {
//            // not a tile entity slot, add tile's slots as target
//            list.add(new SlotRange(0, this.entitySlots - 1, false));
//        }
//
//        int start = this.entitySlots;
//        for(PlayerInventory pi : PLAYER_INVENTORY_INSERT_ORDER) {
//            if (this.playerInventories.contains(pi)) {
//                if ((sourceIndex < start) || (sourceIndex >= (start + pi.slotCount))) {
//                    // ignore the source inventory
//                    list.add(new SlotRange(start, start + pi.slotCount - 1, pi.insertInReverse));
//                }
//                start += pi.slotCount;
//            }
//        }
//
//        return list;
//    }

    public void handleMessageFromServer(final NBTTagCompound compound) {
        if (compound.getSize() > 0) {
            for(final IWidget widget: this.widgets) {
                if (compound.hasKey(widget.getKey(), Constants.NBT.TAG_COMPOUND)) {
                    widget.handleMessageFromServer(compound.getCompoundTag(widget.getKey()));
                }
            }
        }

        if (this.provider != null) {
            this.provider.receiveGuiUpdateTag(compound);
        }
    }

    public void handleMessageFromClient(final NBTTagCompound compound) {
        if (compound.getSize() > 0) {
            for(final IWidget widget: this.widgets) {
                if (compound.hasKey(widget.getKey(), Constants.NBT.TAG_COMPOUND)) {
                    widget.handleMessageFromClient(compound.getCompoundTag(widget.getKey()));
                }
            }
        }
    }

    @Nullable
    public IWidget findWidgetByKey(final String widgetKey) {
        for(final IWidget widget: this.widgets) {
            if (widgetKey.equals(widget.getKey())) {
                return widget;
            }
        }
        return null;
    }

    public List<IWidget> getWidgets() {
        return this.widgets;
    }

    private class SlotRange {
        final int start;
        final int end;
        final boolean reverse;

        SlotRange(final int start, final int end, final boolean reverse) {
            this.start = start;
            this.end = end;
            this.reverse = reverse;
        }
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (this.player instanceof EntityPlayerMP) {
            NBTTagCompound nbt = this.provider.getGuiUpdateTag(true);
            if (nbt == null) {
                nbt = new NBTTagCompound();
            }

            for(final IWidget widget: this.widgets) {
                if (!widget.isDirty()) {
                    continue;
                }

                final NBTTagCompound widgetNbt = widget.getUpdateCompound();
                if ((widgetNbt != null) && (widgetNbt.getSize() > 0)) {
                    nbt.setTag(widget.getKey(), widgetNbt);
                }
                widget.resetDirtyFlag();
            }

            if (nbt.getSize() > 0) {
                MMDPackages.sendToPlayer((EntityPlayerMP)this.player, new NBTBasedPlayerMessage(this.player, nbt));
            }
        }
    }
}
