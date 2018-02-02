package com.mcmoddev.lib.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import javax.annotation.Nullable;
import com.mcmoddev.lib.container.IWidgetContainer;
import com.mcmoddev.lib.container.gui.GuiContext;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.gui.InventoryGrid;
import com.mcmoddev.lib.container.widget.IWidget;
import com.mcmoddev.lib.container.widget.ItemStackHandlerWidget;
import com.mcmoddev.lib.inventory.FilteredItemHandler;
import com.mcmoddev.lib.inventory.ItemHandlerWrapper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

public class ItemInventoryFeature extends BaseFeature implements IClientFeature, IWidgetContainer {
    private final IItemHandlerModifiable internalHandler;
    private final IItemHandlerModifiable externalHandler;

    private int overlayColor = -1;
    private int overlayAlpha = -1;
    private int columns = 9;

    private Function<Integer, ItemStack[]> validStacksGetter;

    public ItemInventoryFeature(String key, int slots) {
        this(key, slots, null, null, null);
    }

    public ItemInventoryFeature(String key, int slots,
                                @Nullable BiPredicate<Integer, ItemStack> insertFilter,
                                @Nullable BiPredicate<Integer, ItemStack> extractFilter) {
        this(key, slots, insertFilter, extractFilter, null);
    }

    public ItemInventoryFeature(String key, int slots,
                                @Nullable BiPredicate<Integer, ItemStack> insertFilter,
                                @Nullable BiPredicate<Integer, ItemStack> extractFilter,
                                @Nullable Function<Integer, ItemStack[]> validStacksGetter) {
        this(key, new ItemStackHandler(slots), insertFilter, extractFilter, validStacksGetter);
    }

    public ItemInventoryFeature(String key, IItemHandlerModifiable handler,
                                @Nullable BiPredicate<Integer, ItemStack> insertFilter,
                                @Nullable BiPredicate<Integer, ItemStack> extractFilter,
                                @Nullable Function<Integer, ItemStack[]> validStacksGetter) {
        super(key);

        this.internalHandler = new ItemHandlerWrapper(handler) {
            @Override
            protected void onChanged(int slot) {
                ItemInventoryFeature.this.setDirty();
            }
        };
        this.externalHandler = new FilteredItemHandler(this.internalHandler, insertFilter, extractFilter);
        this.validStacksGetter = validStacksGetter;
    }

    public IItemHandlerModifiable getInternalHandler() {
        return this.internalHandler;
    }

    public IItemHandlerModifiable getExternalHandler() {
        return this.externalHandler;
    }

    @Override
    protected void writeToNBT(NBTTagCompound tag) {
        if (this.internalHandler instanceof INBTSerializable) {
            // TODO: hopefully this won't cause problems, wish java had real generics
            //noinspection unchecked
            INBTSerializable<NBTTagCompound> serializable = (INBTSerializable<NBTTagCompound>)this.internalHandler;

            tag.setTag("stacks", serializable.serializeNBT());
        }
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        if (this.internalHandler instanceof INBTSerializable) {
            // TODO: hopefully this won't cause problems, wish java had real generics
            //noinspection unchecked
            INBTSerializable<NBTTagCompound> serializable = (INBTSerializable<NBTTagCompound>) this.internalHandler;
            if (nbt.hasKey("stacks", Constants.NBT.TAG_COMPOUND)) {
                NBTTagCompound stacksNBT = nbt.getCompoundTag("stacks");
                serializable.deserializeNBT(stacksNBT);
            } else {
                // TODO: find a better way to reset an item stack handler
                for(int slot = 0; slot < this.internalHandler.getSlots(); slot++) {
                    this.internalHandler.setStackInSlot(slot, ItemStack.EMPTY);
                }
            }
        }
    }
//
//    public ItemInventoryFeature setSlotPositioner(Function<Integer, Vec2i> slotPositioner) {
//        this.slotPositioner = slotPositioner;
//        return this;
//    }
//
//    public ItemInventoryFeature setSlotPositions(int left, int top, int slotsPerRow) {
//        return this.setSlotPositioner(slot -> {
//            int x = left + (slot % slotsPerRow) * 18;
//            int y = top + (slot / slotsPerRow) * 18;
//            return new Vec2i(x, y);
//        });
//    }

//    @Override
//    public List<Slot> getContainerSlots(MMDContainer container) {
//        List<Slot> slots = Lists.newArrayList();
//
//        if (this.slotPositioner != null) {
//            IItemHandler handler = this.externalHandler;
//            for(int index = 0; index < handler.getSlots(); index++) {
//                Vec2i position = this.slotPositioner.apply(index);
//                if (position != null) {
//                    Slot slot = new SlotItemHandler(handler, index, position.x, position.y);
//                    slots.add(slot);
//                }
//            }
//        }
//
//        return slots;
//    }

    public ItemInventoryFeature setOverlayColor(int color) {
        this.overlayColor = color;
        this.overlayAlpha = -1;
        return this;
    }

    public ItemInventoryFeature setOverlayColor(int color, int alpha) {
        this.overlayColor = color;
        this.overlayAlpha = alpha;
        return this;
    }

    public int getColumns() {
        return this.columns;
    }

    public ItemInventoryFeature setColumns(int columns) {
        this.columns = columns;
        return this;
    }

    @Override
    public List<IWidget> getWidgets(GuiContext context) {
        return new ArrayList<IWidget>() {{
            add(new ItemStackHandlerWidget(
                ItemInventoryFeature.this.getKey() + "_slots",
                ItemInventoryFeature.this.getExternalHandler()));
        }};
    }

    @Override
    public IWidgetGui getRootWidgetGui(GuiContext context) {
//        CanvasLayout layout = new CanvasLayout();
//
//        if ((this.overlayColor != -1) && (this.slotPositioner != null)) {
//            int left = Integer.MAX_VALUE;
//            int top = Integer.MAX_VALUE;
//            int right = Integer.MIN_VALUE;
//            int bottom = Integer.MIN_VALUE;
//            for(int index = 0; index < this.externalHandler.getSlots(); index++) {
//                Vec2i position = this.slotPositioner.apply(index);
//                left = Math.min(position.x, left);
//                top = Math.min(position.y, top);
//                right = Math.max(position.x + 18, right);
//                bottom = Math.max(position.y + 18, bottom);
//            }
//
//            if (this.overlayAlpha > -1) {
//                layout.addPiece(new ColorOverlayPiece(right - left, bottom - top, this.overlayColor, this.overlayAlpha), left, top);
//            }
//            else {
//                layout.addPiece(new ColorOverlayPiece(right - left, bottom - top, this.overlayColor), left, top);
//            }
//        }
//
//        return layout;
        InventoryGrid grid = new InventoryGrid(this.columns, this.getKey() + "_slots");
//        ItemStackHandlerGrid grid = new ItemStackHandlerGrid(this.internalHandler, this.getKey(), this.columns);
        if (this.overlayColor != 0) {
            if (this.overlayAlpha > -1) {
                grid.setColorOverlay(this.overlayColor, this.overlayAlpha);
            }
            else {
                grid.setColorOverlay(this.overlayColor);
            }
        }
        return grid;
    }

    @Override
    public NBTTagCompound getGuiUpdateTag(boolean resetDirtyFlag) {
        // gui slots should take care of this on GUIs... and outside GUIs we shouldn't care.
        return null;
    }
}
