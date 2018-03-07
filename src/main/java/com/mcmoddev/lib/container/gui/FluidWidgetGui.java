package com.mcmoddev.lib.container.gui;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import com.mcmoddev.lib.container.gui.sprite.FluidSprite;
import com.mcmoddev.lib.container.gui.util.Size2D;
import com.mcmoddev.lib.util.MathUtils;
import net.minecraftforge.fluids.Fluid;

public class FluidWidgetGui extends BaseWidgetGui {
    private final Supplier<Fluid> fluidGetter;
    private final Supplier<Float> percentGetter;
    private final GuiPieceLayer layer;

    public FluidWidgetGui(final Fluid fluid, final int width, final int height, final GuiPieceLayer layer) {
        this(fluid, width, height, layer, null);
    }

    public FluidWidgetGui(final Fluid fluid, final int width, final int height, final GuiPieceLayer layer, @Nullable final Supplier<Float> percentGetter) {
        this(() -> fluid, width, height, layer, percentGetter);
    }

    public FluidWidgetGui(final Supplier<Fluid> fluidGetter, final int width, final int height, final GuiPieceLayer layer) {
        this(fluidGetter, width, height, layer, null);
    }

    public FluidWidgetGui(final Supplier<Fluid> fluidGetter, final int width, final int height, final GuiPieceLayer layer, @Nullable final Supplier<Float> percentGetter) {
        super(width, height);

        this.fluidGetter = fluidGetter;
        this.layer = layer;
        this.percentGetter = percentGetter;
    }

    @Override
    public GuiPieceLayer getLayer() {
        return this.layer;
    }

    @Override
    public void drawBackgroundLayer(final MMDGuiContainer container, final float partialTicks, final int mouseX, final int mouseY) {
        this.draw(container);
    }

    @Override
    public void drawMiddleLayer(final MMDGuiContainer container, final float partialTicks, final int mouseX, final int mouseY) {
        this.draw(container);
    }

    @Override
    public void drawForegroundLayer(final MMDGuiContainer container, final int mouseX, final int mouseY) {
        this.draw(container);
    }

    @Override
    public void drawForegroundTopLayer(final MMDGuiContainer container, final int mouseX, final int mouseY) {
        this.draw(container);
    }

    private void draw(final MMDGuiContainer container) {
        final Fluid fluid = (this.fluidGetter == null) ? null : this.fluidGetter.get();
        if (fluid == null) {
            return;
        }

        final Float percent = MathUtils.clampf((this.percentGetter == null) ? 1f : this.percentGetter.get(), 0f, 1f);
        final Size2D size = this.getSize();
        final int height = MathUtils.clampi(Math.round((float)size.height * percent), 0, size.height);
        if (height == 0) {
            // no visible pixels
            return;
        }

        final int top = fluid.isGaseous() ? 0 : size.height - height;
        final FluidSprite sprite = new FluidSprite(fluid, size.width, height);
        sprite.draw(container, 0, top, size.width, height);
    }
}
