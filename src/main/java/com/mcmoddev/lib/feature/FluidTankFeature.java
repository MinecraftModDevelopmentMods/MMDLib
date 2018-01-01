package com.mcmoddev.lib.feature;

import java.util.function.Predicate;
import javax.annotation.Nullable;
import com.mcmoddev.lib.gui.GuiContext;
import com.mcmoddev.lib.gui.GuiSprites;
import com.mcmoddev.lib.gui.IGuiPiece;
import com.mcmoddev.lib.gui.IGuiPieceProvider;
import com.mcmoddev.lib.gui.layout.CanvasLayout;
import com.mcmoddev.lib.gui.piece.GuiBackgroundSpritePiece;
import com.mcmoddev.lib.gui.piece.GuiForegroundSpritePiece;
import com.mcmoddev.lib.inventory.FilteredFluidTank;
import com.mcmoddev.lib.inventory.IFluidTankModifiable;
import com.mcmoddev.lib.inventory.SimpleFluidTank;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

public class FluidTankFeature extends BaseFeature implements IClientFeature, IGuiPieceProvider {
    private final IFluidTankModifiable internalTank;
    private final IFluidTankModifiable externalTank;

    private int left = 0;
    private int top = 0;

    public FluidTankFeature(String key, int capacity,
                            @Nullable Predicate<FluidStack> fillFilter,
                            @Nullable Predicate<FluidStack> drainFilter) {
        this(key, new SimpleFluidTank(capacity), fillFilter, drainFilter);
    }

    public FluidTankFeature(String key, Fluid fluid, int capacity,
                            @Nullable Predicate<FluidStack> drainFilter) {
        this(key, new SimpleFluidTank(capacity),
            stack -> (stack.getFluid() == fluid),
            drainFilter);
    }

    public FluidTankFeature(String key, IFluidTankModifiable tank,
                            @Nullable Predicate<FluidStack> fillFilter,
                            @Nullable Predicate<FluidStack> drainFilter) {
        super(key);

        this.internalTank = tank;
        this.externalTank = new FilteredFluidTank(this.internalTank, fillFilter, drainFilter);
    }

    public IFluidTankModifiable getInternalTank() {
        return this.internalTank;
    }

    public IFluidTankModifiable getExternalTank() {
        return this.externalTank;
    }

    @Override
    protected void writeToNBT(NBTTagCompound tag) {
        FluidStack fluid = this.internalTank.getFluid();
        if (fluid != null) {
            NBTTagCompound fluidTag = new NBTTagCompound();
            fluid.writeToNBT(fluidTag);
            tag.setTag("fluid", fluidTag);
        }
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        if (nbt.hasKey("fluid", Constants.NBT.TAG_COMPOUND)) {
            NBTTagCompound fluidTag = nbt.getCompoundTag("fluid");
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(fluidTag);
            this.internalTank.setFluid(fluid);
        }
    }

    public FluidTankFeature setPosition(int left, int top) {
        this.left = left;
        this.top = top;

        return this;
    }

    @Override
    public IGuiPiece getRootPiece(GuiContext context) {
        CanvasLayout layout = new CanvasLayout();

        layout.addPiece(new GuiBackgroundSpritePiece(GuiSprites.TANK_CONTAINER), 0, 0);
        layout.addPiece(
            new GuiForegroundSpritePiece(GuiSprites.TANK_OVERLAY, GuiSprites.TANK_CONTAINER.getWidth(), GuiSprites.TANK_CONTAINER.getHeight()),
            0, 0);

        return layout;
    }
}
