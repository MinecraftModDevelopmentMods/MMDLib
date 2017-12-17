package com.mcmoddev.lib.container;

import java.util.List;
import javax.annotation.Nullable;
import com.google.common.collect.Lists;
import com.mcmoddev.lib.gui.IGuiHolder;
import com.mcmoddev.lib.network.MMDPackages;
import com.mcmoddev.lib.network.NBTBasedPlayerMessage;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SuppressWarnings("WeakerAccess")
public class MMDContainer extends Container {
    private final static double MAX_INTERACT_DISTANCE = 64.0;
    private final static PlayerInventory[] PLAYER_INVENTORY_INSERT_ORDER = {
        PlayerInventory.EQUIPMENT,
        PlayerInventory.QUICKBAR,
        PlayerInventory.INVENTORY,
        PlayerInventory.OFF_HAND
    };

    private final IGuiHolder holder;
    private final EntityPlayer player;

    private int entitySlots;
    private List<PlayerInventory> playerInventories;

    public MMDContainer(IGuiHolder holder, EntityPlayer player) {
        super();

        this.holder = holder;
        this.player = player;

        IContainerSlotsProvider slotsProvider = this.holder.getSlotsProvider();
        List<Slot> entitySlots = (slotsProvider != null)
            ? slotsProvider.getContainerSlots(this)
            : Lists.newArrayList();
        this.entitySlots = entitySlots.size();
        for(Slot slot: entitySlots) {
            this.addSlotToContainer(slot);
        }

        this.playerInventories = Lists.newArrayList();
        List<PlayerInventoryInfo> playerInventoryInfos = this.getPlayerInventories();
        for(PlayerInventoryInfo info : playerInventoryInfos) {
            this.playerInventories.add(info.inventory);
            if (info.inventory == PlayerInventory.EQUIPMENT) {
                // special handling for equipment slots (stack size + background texture)
                this.addPlayerEquipmentSlots(this.player, info);
            } else if (info.inventory == PlayerInventory.OFF_HAND) {
                // special handling for equipment slots (background texture)
                this.addPlayerOffhandSlot(player, info);
            } else {
                this.addPlayerSlots(this.player, info);
            }
        }
    }

    protected List<PlayerInventoryInfo> getPlayerInventories() {
        IPlayerInventoryProvider provider = this.holder.getPlayerInventoryProvider();
        return (provider != null) ? provider.getPlayerSlots(this) : Lists.newArrayList();
    }

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        return this.holder.isValid() && (this.holder.getDistance(playerIn) <= MAX_INTERACT_DISTANCE);
    }

    private void addPlayerSlots(EntityPlayer player, PlayerInventoryInfo info) {
        InventoryPlayer playerInventory = player.inventory;

        for (int slot = 0; slot < info.inventory.slotCount; slot++) {
            int row = slot / info.slotsPerRow;
            int column = slot % info.slotsPerRow;
            this.addSlotToContainer(new Slot(playerInventory, info.inventory.slotStart + slot,
                info.guiLeft + column * 18,
                info.guiTop + row * 18));
        }
    }

    private void addPlayerEquipmentSlots(EntityPlayer player, PlayerInventoryInfo info) {
        InventoryPlayer playerInventory = player.inventory;

        for (EntityEquipmentSlot slot : EntityEquipmentSlot.values()) {
            if (slot.getSlotType() != EntityEquipmentSlot.Type.ARMOR) {
                continue;
            }
            int row = slot.getIndex() / info.slotsPerRow;
            int column = slot.getIndex() % info.slotsPerRow;

            this.addSlotToContainer(new Slot(playerInventory, 36 + slot.getIndex(),
                info.guiLeft + column * 18,
                info.guiTop + row * 18) {
                private EntityEquipmentSlot slotType;
                private EntityPlayer player;

                @Override
                public int getSlotStackLimit() {
                    return 1;
                }

                @Override
                public boolean isItemValid(ItemStack stack) {
                    return stack.getItem().isValidArmor(stack, this.slotType, this.player);
                }

                @Override
                public boolean canTakeStack(EntityPlayer playerIn) {
                    ItemStack itemStack = this.getStack();
                    return !(!itemStack.isEmpty() && !playerIn.isCreative() && EnchantmentHelper.hasBindingCurse(itemStack)) && super.canTakeStack(playerIn);
                }

                @Override
                @SideOnly(Side.CLIENT)
                public String getSlotTexture() {
                    return ItemArmor.EMPTY_SLOT_NAMES[this.slotType.getIndex()];
                }

                private Slot setPlayerAndSlotType(EntityPlayer player, EntityEquipmentSlot slotType) {
                    this.player = player;
                    this.slotType = slotType;
                    return this;
                }
            }.setPlayerAndSlotType(player, slot));
        }
    }

    private void addPlayerOffhandSlot(EntityPlayer player, PlayerInventoryInfo info) {
        InventoryPlayer playerInventory = player.inventory;

        this.addSlotToContainer(new Slot(playerInventory, 40, info.guiLeft, info.guiTop) {
            @Override
            @SideOnly(Side.CLIENT)
            public String getSlotTexture() {
                return "minecraft:items/empty_armor_slot_shield";
            }
        });
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        ItemStack copyStack = ItemStack.EMPTY;
        Slot slot = this.inventorySlots.get(index);

        if ((slot != null) && slot.getHasStack()) {
            ItemStack origStack = slot.getStack();
            copyStack = origStack.copy();

            boolean merged = false;
            for (SlotRange range : this.getSlotsRange(index)) {
                if (super.mergeItemStack(origStack, range.start, range.end, range.reverse)) {
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                return ItemStack.EMPTY;
            }
        }

        return copyStack;
    }

    private List<SlotRange> getSlotsRange(int sourceIndex) {
        List<SlotRange> list = Lists.newArrayList();

        if (sourceIndex >= this.entitySlots) {
            // not a tile entity slot, add tile's slots as target
            list.add(new SlotRange(0, this.entitySlots - 1, false));
        }

        int start = this.entitySlots;
        for(PlayerInventory pi : PLAYER_INVENTORY_INSERT_ORDER) {
            if (this.playerInventories.contains(pi)) {
                if ((sourceIndex < start) || (sourceIndex >= (start + pi.slotCount))) {
                    // ignore the source inventory
                    list.add(new SlotRange(start, start + pi.slotCount - 1, pi.insertInReverse));
                }
                start += pi.slotCount;
            }
        }

        return list;
    }

    @Nullable
    public IMessage handleMessageFromServer(NBTTagCompound compound) {
        if (this.holder != null) {
            return this.holder.receiveGuiUpdateTag(compound);
        }
        return null;
    }

    private class SlotRange {
        final int start;
        final int end;
        final boolean reverse;

        SlotRange(int start, int end, boolean reverse) {
            this.start = start;
            this.end = end;
            this.reverse = reverse;
        }
    }

    @Override
    @SideOnly(Side.SERVER)
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (this.player instanceof EntityPlayerMP) {
            NBTTagCompound nbt = this.holder.getGuiUpdateTag(true);
            if ((nbt != null) && (nbt.getSize() > 0)) {
                MMDPackages.sendToPlayer((EntityPlayerMP)this.player, new NBTBasedPlayerMessage(this.player, nbt));
            }
        }
    }
}
