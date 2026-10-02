package zone.moddev.mc.mmdlib.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import zone.moddev.mc.mmdlib.capability.ICapabilitiesContainer;
import zone.moddev.mc.mmdlib.container.IWidgetContainer;
import zone.moddev.mc.mmdlib.container.gui.GuiContext;
import zone.moddev.mc.mmdlib.container.gui.IWidgetGui;
import zone.moddev.mc.mmdlib.container.gui.InventoryGrid;
import zone.moddev.mc.mmdlib.container.widget.IWidget;
import zone.moddev.mc.mmdlib.container.widget.ItemStackHandlerWidget;
import zone.moddev.mc.mmdlib.inventory.FilteredItemHandler;
import zone.moddev.mc.mmdlib.inventory.ItemHandlerWrapper;
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

@SuppressWarnings("unchecked")
public class ItemInventoryFeature extends BaseFeature implements IClientFeature, IWidgetContainer, ICapabilityProvider {
    private final IItemHandlerModifiable internalHandler;
    private final FilteredItemHandler externalHandler;

    private int overlayColor = -1;
    private int overlayAlpha = -1;
    private int columns = 9;

    public ItemInventoryFeature(final String key, final int slots) {
        this(key, slots, null, null);
    }

    public ItemInventoryFeature(final String key, final int slots,
                                @Nullable final BiPredicate<Integer, ItemStack> insertFilter,
                                @Nullable final BiPredicate<Integer, ItemStack> extractFilter) {
        this(key, new ItemStackHandler(slots), insertFilter, extractFilter);
    }

    public ItemInventoryFeature(final String key, final IItemHandlerModifiable handler,
                                @Nullable final BiPredicate<Integer, ItemStack> insertFilter,
                                @Nullable final BiPredicate<Integer, ItemStack> extractFilter) {
        super(key);

        this.internalHandler = new ItemHandlerWrapper(handler) {
            @Override
            protected void onChanged(final int slot) {
                ItemInventoryFeature.this.setDirty();
            }
        };
        this.externalHandler = new FilteredItemHandler(this.internalHandler, insertFilter, extractFilter);

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
            // Serializable item handlers are expected to use compound tags.
            //noinspection unchecked
            final INBTSerializable<NBTTagCompound> serializable = (INBTSerializable<NBTTagCompound>)this.internalHandler;

            tag.setTag("stacks", serializable.serializeNBT());
        }
    }

    @Override
    public void deserializeNBT(final NBTTagCompound nbt) {
        if (this.internalHandler instanceof INBTSerializable) {
            // Serializable item handlers are expected to use compound tags.
            //noinspection unchecked
            final INBTSerializable<NBTTagCompound> serializable = (INBTSerializable<NBTTagCompound>) this.internalHandler;
            if (nbt.hasKey("stacks", Constants.NBT.TAG_COMPOUND)) {
                final NBTTagCompound stacksNBT = nbt.getCompoundTag("stacks");
                serializable.deserializeNBT(stacksNBT);
            } else {
                // Clear every slot when the saved inventory tag is absent.
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

    @SuppressWarnings("serial")
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
