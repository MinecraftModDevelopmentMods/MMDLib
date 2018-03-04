package com.mcmoddev.lib.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.mcmoddev.lib.capability.ICapabilitiesContainer;
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
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

@SuppressWarnings({"WeakerAccess", "unused", "UnusedReturnValue"})
public class ItemInventoryFeature extends BaseFeature implements IClientFeature, IWidgetContainer, ICapabilityProvider {
    private final IItemHandlerModifiable internalHandler;
    private final FilteredItemHandler externalHandler;

    private int overlayColor = -1;
    private int overlayAlpha = -1;
    private int columns = 9;

//    private Function<Integer, ItemStack[]> validStacksGetter;

    public ItemInventoryFeature(final String key, final int slots) {
        this(key, slots, null, null/*, null*/);
    }

    public ItemInventoryFeature(final String key, final int slots,
                                @Nullable final BiPredicate<Integer, ItemStack> insertFilter,
                                @Nullable final BiPredicate<Integer, ItemStack> extractFilter) {
        this(key, new ItemStackHandler(slots), insertFilter, extractFilter/*, null*/);
    }

    // TODO: actually implement this at some point
//    public ItemInventoryFeature(String key, int slots,
//                                @Nullable BiPredicate<Integer, ItemStack> insertFilter,
//                                @Nullable BiPredicate<Integer, ItemStack> extractFilter,
//                                @Nullable Function<Integer, ItemStack[]> validStacksGetter) {
//        this(key, new ItemStackHandler(slots), insertFilter, extractFilter, validStacksGetter);
//    }

    public ItemInventoryFeature(final String key, final IItemHandlerModifiable handler,
                                @Nullable final BiPredicate<Integer, ItemStack> insertFilter,
                                @Nullable final BiPredicate<Integer, ItemStack> extractFilter/*,
                                @Nullable Function<Integer, ItemStack[]> validStacksGetter*/) {
        super(key);

        this.internalHandler = new ItemHandlerWrapper(handler) {
            @Override
            protected void onChanged(final int slot) {
                ItemInventoryFeature.this.setDirty();
            }
        };
        this.externalHandler = new FilteredItemHandler(this.internalHandler, insertFilter, extractFilter);

//        // TODO: make this happen into an "ghosted" Slot
//        this.validStacksGetter = validStacksGetter;
    }

    public IItemHandlerModifiable getInternalHandler() {
        return this.internalHandler;
    }

    public IItemHandlerModifiable getExternalHandler() {
        return this.externalHandler;
    }

    @Override
    protected void writeToNBT(final NBTTagCompound tag) {
        if (this.internalHandler instanceof INBTSerializable) {
            // TODO: hopefully this won't cause problems, wish java had real generics
            //noinspection unchecked
            final INBTSerializable<NBTTagCompound> serializable = (INBTSerializable<NBTTagCompound>)this.internalHandler;

            tag.setTag("stacks", serializable.serializeNBT());
        }
    }

    @Override
    public void deserializeNBT(final NBTTagCompound nbt) {
        if (this.internalHandler instanceof INBTSerializable) {
            // TODO: hopefully this won't cause problems, wish java had real generics
            //noinspection unchecked
            final INBTSerializable<NBTTagCompound> serializable = (INBTSerializable<NBTTagCompound>) this.internalHandler;
            if (nbt.hasKey("stacks", Constants.NBT.TAG_COMPOUND)) {
                final NBTTagCompound stacksNBT = nbt.getCompoundTag("stacks");
                serializable.deserializeNBT(stacksNBT);
            } else {
                // TODO: find a better way to reset an item stack handler
                for(int slot = 0; slot < this.internalHandler.getSlots(); slot++) {
                    this.internalHandler.setStackInSlot(slot, ItemStack.EMPTY);
                }
            }
        }
    }

    @Override
    public void initCapabilities(final ICapabilitiesContainer container) {
        container.addCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, f -> this.externalHandler);
    }

    public ItemInventoryFeature setOverlayColor(final int color) {
        this.overlayColor = color;
        this.overlayAlpha = -1;
        return this;
    }

    public ItemInventoryFeature setOverlayColor(final int color, final int alpha) {
        this.overlayColor = color;
        this.overlayAlpha = alpha;
        return this;
    }

    public int getColumns() {
        return this.columns;
    }

    public ItemInventoryFeature setColumns(final int columns) {
        this.columns = columns;
        return this;
    }

    @Override
    public List<IWidget> getWidgets(final GuiContext context) {
        return new ArrayList<IWidget>() {{
            add(new ItemStackHandlerWidget(
                ItemInventoryFeature.this.getKey() + "_slots",
                ItemInventoryFeature.this.internalHandler,
                ItemInventoryFeature.this.externalHandler));
        }};
    }

    @Override
    public IWidgetGui getRootWidgetGui(final GuiContext context) {
        final InventoryGrid grid = new InventoryGrid(this.columns, this.getKey() + "_slots");
        if (this.overlayColor != -1) {
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
    public NBTTagCompound getGuiUpdateTag(final boolean resetDirtyFlag) {
        // gui slots should take care of this on GUIs... and outside GUIs we shouldn't care.
        return null;
    }

    @Nullable
    @Override
    public NBTTagCompound getLoadUpdateTag() {
        return super.getLoadUpdateTag();
    }

    @Override
    public boolean hasCapability(@Nonnull final Capability<?> capability, @Nullable final EnumFacing facing) {
        return (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY);
    }

    @Nullable
    @Override
    public <T> T getCapability(@Nonnull final Capability<T> capability, @Nullable final EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(this.getExternalHandler());
        }
        return null;
    }
}
