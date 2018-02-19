package com.mcmoddev.lib.feature;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import com.mcmoddev.lib.container.IWidgetContainer;
import com.mcmoddev.lib.container.gui.BaseWidgetGui;
import com.mcmoddev.lib.container.gui.FluidWidgetGui;
import com.mcmoddev.lib.container.gui.GuiContext;
import com.mcmoddev.lib.container.gui.GuiPieceLayer;
import com.mcmoddev.lib.container.gui.GuiSprites;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.gui.MMDGuiContainer;
import com.mcmoddev.lib.container.gui.SpriteBackgroundGui;
import com.mcmoddev.lib.container.gui.SpriteForegroundGui;
import com.mcmoddev.lib.container.gui.layout.CanvasLayout;
import com.mcmoddev.lib.container.widget.ActionWidget;
import com.mcmoddev.lib.container.widget.IWidget;
import com.mcmoddev.lib.inventory.FilteredFluidTank;
import com.mcmoddev.lib.inventory.IFluidTankModifiable;
import com.mcmoddev.lib.inventory.IResponsiveFluidTank;
import com.mcmoddev.lib.inventory.ResponsiveFluidTankWrapper;
import com.mcmoddev.lib.inventory.SimpleFluidTank;
import com.mcmoddev.lib.util.FluidUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;

@SuppressWarnings({"WeakerAccess", "unused"})
public class FluidTankFeature extends BaseFeature implements IClientFeature, IWidgetContainer {
    private final IFluidTankModifiable internalTank;
    private final IFluidTank externalTank;

    public FluidTankFeature(final String key, final int capacity,
                            @Nullable final Predicate<FluidStack> fillFilter,
                            @Nullable final Predicate<FluidStack> drainFilter) {
        this(key, new SimpleFluidTank(capacity), fillFilter, drainFilter);
    }

    public FluidTankFeature(final String key, final Fluid fluid, final int capacity,
                            @Nullable final Predicate<FluidStack> drainFilter) {
        this(key, new SimpleFluidTank(capacity),
            stack -> (stack.getFluid() == fluid),
            drainFilter);
    }

    public FluidTankFeature(final String key, final IFluidTankModifiable tank,
                            @Nullable final Predicate<FluidStack> fillFilter,
                            @Nullable final Predicate<FluidStack> drainFilter) {
        super(key);

        if (tank instanceof IResponsiveFluidTank) {
            ((IResponsiveFluidTank)tank).addResponsiveTarget(t -> this.onTankContentsChanged());
            this.internalTank = tank;
        }
        else {
            this.internalTank = new ResponsiveFluidTankWrapper(tank, t -> this.onTankContentsChanged());
        }
        this.externalTank = new FilteredFluidTank(this.internalTank, fillFilter, drainFilter);
    }

    public IFluidTankModifiable getInternalTank() {
        return this.internalTank;
    }

    public IFluidTank getExternalTank() {
        return this.externalTank;
    }

    private void onTankContentsChanged() {
        this.setDirty();
    }

    @Override
    protected void writeToNBT(final NBTTagCompound tag) {
        final FluidStack fluid = this.internalTank.getFluid();
        if (fluid != null) {
            final NBTTagCompound fluidTag = new NBTTagCompound();
            fluid.writeToNBT(fluidTag);
            tag.setTag("fluid", fluidTag);
        }
    }

    @Override
    public void deserializeNBT(final NBTTagCompound nbt) {
        if (nbt.hasKey("fluid", Constants.NBT.TAG_COMPOUND)) {
            final NBTTagCompound fluidTag = nbt.getCompoundTag("fluid");
            final FluidStack fluid = FluidStack.loadFluidStackFromNBT(fluidTag);
            this.internalTank.setFluid(fluid);
        } else {
            this.internalTank.setFluid(null);
        }
    }

    @Override
    public List<IWidget> getWidgets(final GuiContext context) {
        return Collections.singletonList(new FluidTankHandler());
    }

    @Override
    public IWidgetGui getRootWidgetGui(final GuiContext context) {
        final CanvasLayout layout = new CanvasLayout();

        layout.addPiece(new SpriteBackgroundGui(GuiSprites.TANK_CONTAINER), 0, 0);
        layout.addPiece(new FluidWidgetGui(
            () -> {
                final FluidStack stack = this.internalTank.getFluid();
                return (stack == null) ? null : stack.getFluid();
            },
            GuiSprites.TANK_CONTAINER.getWidth() - 6,
            GuiSprites.TANK_CONTAINER.getHeight() - 6,
            GuiPieceLayer.MIDDLE,
            () -> {
                final FluidStack stack = this.internalTank.getFluid();
                return ((stack == null) || (stack.amount == 0)) ? 0f : ((float)stack.amount / (float)this.internalTank.getCapacity());
            }
        ), 3, 3);
        layout.addPiece(
            new SpriteForegroundGui(GuiSprites.TANK_OVERLAY, GuiSprites.TANK_CONTAINER.getWidth(), GuiSprites.TANK_CONTAINER.getHeight()),
            0, 0);

        layout.addPiece(new FluidTankHandlerGui(GuiSprites.TANK_CONTAINER.getWidth(), GuiSprites.TANK_CONTAINER.getHeight()), 0, 0);

        return layout;
    }

    @Nullable
    public FluidStack getFluid() {
        return this.internalTank.getFluid();
    }

    private class FluidTankHandler extends ActionWidget {
        protected FluidTankHandler() {
            super(FluidTankFeature.this.getKey() + "_handler");

            this.setServerSideConsumer(this::serverCallback);
        }

        private void serverCallback(final NBTTagCompound data) {
            if (data.hasKey("action", Constants.NBT.TAG_STRING)) {
                final String action = data.getString("action");
                final EntityPlayer player = this.getPlayer();
                boolean stackChanged = false;
                if ((player != null) && action.equals("fill")) {
                    final ItemStack holdingStack = player.inventory.getItemStack();
                    final IFluidTank tank = FluidTankFeature.this.getExternalTank();
                    if (FluidUtils.canFillFrom(tank, holdingStack)) {
                        player.inventory.setItemStack(FluidUtils.fillFrom(tank, holdingStack));
                        stackChanged = true;
                    }
                } else if ((player != null) && action.equals("drain")) {
                    final ItemStack holdingStack = player.inventory.getItemStack();
                    final IFluidTank tank = FluidTankFeature.this.getExternalTank();
                    if (FluidUtils.canDrainInto(tank, holdingStack)) {
                        player.inventory.setItemStack(FluidUtils.drainInto(tank, holdingStack));
                        stackChanged = true;
                    }
                }

                if (stackChanged && (player instanceof EntityPlayerMP)) {
                    ((EntityPlayerMP) player).connection.sendPacket(
                        new SPacketSetSlot(-1, 0, player.inventory.getItemStack())
                    );
                }
            }
        }
    }

    public boolean supportsClickToFill() {
        return true;
    }

    public boolean supportsClickToDrain() {
        return true;
    }

    private class FluidTankHandlerGui extends BaseWidgetGui {
        FluidTankHandlerGui(final int width, final int height) {
            super(width, height);
        }

        @Override
        public GuiPieceLayer[] getLayers() {
            return new GuiPieceLayer[0];
        }

        @Override
        public void getTooltip(final List<String> lines) {
            final FluidStack fluid = FluidTankFeature.this.getFluid();
            if ((fluid == null) || (fluid.amount == 0)) {
                lines.add(TextFormatting.DARK_GRAY + "EMPTY TANK");
            }
            else {
                lines.add(TextFormatting.AQUA + String.format("%d mb of %s", fluid.amount, fluid.getLocalizedName()));
            }

            boolean canFill = FluidTankFeature.this.supportsClickToFill();
            boolean canDrain = FluidTankFeature.this.supportsClickToDrain();

            if (canFill | canDrain) {
                final ItemStack holdingStack = Minecraft.getMinecraft().player.inventory.getItemStack();
                canFill = canFill && FluidUtils.canFillFrom(FluidTankFeature.this.getExternalTank(), holdingStack);
                canDrain = canDrain && FluidUtils.canDrainInto(FluidTankFeature.this.getExternalTank(), holdingStack);

                final String hoveringText = holdingStack.getDisplayName();

                if (canFill && canDrain) {
                    lines.add("Right click to fill tank from " + hoveringText + ".");
                    lines.add("Left click to drain tank into " + hoveringText + ".");
                } else if (canFill) {
                    lines.add("Click to fill tank from " + hoveringText + ".");
                } else if (canDrain) {
                    lines.add("Click to drain tank into " + hoveringText + ".");
                }
            }
        }

        @Override
        public boolean mouseReleased(final MMDGuiContainer container, final int mouseX, final int mouseY, final int mouseButton) {
            boolean canFill = FluidTankFeature.this.supportsClickToFill();
            boolean canDrain = FluidTankFeature.this.supportsClickToDrain();

            if (canFill | canDrain) {
                // TODO: maybe just do the testing on server side too... to be sure everything is in sync
                final ItemStack holdingStack = Minecraft.getMinecraft().player.inventory.getItemStack();
                canFill = canFill && FluidUtils.canFillFrom(FluidTankFeature.this.getExternalTank(), holdingStack);
                canDrain = canDrain && FluidUtils.canDrainInto(FluidTankFeature.this.getExternalTank(), holdingStack);
                String action = null;

                if (canFill && canDrain) {
                    action = (mouseButton == 1) ? "fill" : "drain";
                }
                else if (canFill) {
                    action = "fill";
                }
                else if (canDrain) {
                    action = "drain";
                }

                if (action != null) {
                    final NBTTagCompound actionTag = new NBTTagCompound();
                    actionTag.setString("action", action);
                    ((FluidTankHandler)container.findWidgetByKey(FluidTankFeature.this.getKey() + "_handler"))
                        .actionPerformed(actionTag);
                }
            }

            return false;
        }
    }
}
