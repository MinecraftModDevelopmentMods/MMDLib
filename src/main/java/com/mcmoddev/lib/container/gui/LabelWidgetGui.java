package com.mcmoddev.lib.container.gui;

import java.awt.Color;
import com.mcmoddev.lib.container.gui.util.Size2D;
import com.mcmoddev.lib.container.widget.DataWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

public class LabelWidgetGui extends BaseWidgetGui {
    private String text;
    private Size2D measured = null;
    private FontRenderer font;
    private int color;

    private final GuiPieceLayer layer;

    private String dataWidgetKey = null;
    private String dataWidgetDataKey = null;

    public LabelWidgetGui() {
        this("", Minecraft.getMinecraft().fontRenderer, GuiPieceLayer.FOREGROUND, Color.black.getRGB());
    }

    public LabelWidgetGui(final String text) {
        this(text, Minecraft.getMinecraft().fontRenderer, GuiPieceLayer.FOREGROUND, Color.black.getRGB());
    }

    public LabelWidgetGui(final String text, final FontRenderer font, final GuiPieceLayer layer, final int color) {
        this.setText(text);
        this.font = font;
        this.layer = layer;
        this.color = color;
    }

    @Override
    public GuiPieceLayer getLayer() {
        return this.layer;
    }

    public String getText() {
        return this.text;
    }

    public boolean setText(final String text) {
        if ((this.text != null) && (this.text.compareTo(text) == 0)) {
            return false; // same text
        }

        this.text = text;
        this.measured = null;
        return true;
    }

    public LabelWidgetGui connectDataWidget(final String widgetKey, final String dataKey) {
        this.dataWidgetKey = widgetKey;
        this.dataWidgetDataKey = dataKey;
        return this;
    }

    @Override
    public Size2D getSize() {
        if (this.measured == null) {
            final int width = this.font.getStringWidth(this.text);
            final int height = this.font.FONT_HEIGHT;
            this.measured = new Size2D(width, height);
        }

        return this.measured;
    }

    @Override
    public void drawBackgroundLayer(final MMDGuiContainer container, final float partialTicks, final int mouseX, final int mouseY) {
        this.drawText(container); // will only be called if the correct layer is set
    }

    @Override
    public void drawMiddleLayer(final MMDGuiContainer container, final float partialTicks, final int mouseX, final int mouseY) {
        this.drawText(container); // will only be called if the correct layer is set
    }

    @Override
    public void drawForegroundLayer(final MMDGuiContainer container, final int mouseX, final int mouseY) {
        this.drawText(container); // will only be called if the correct layer is set
    }

    @Override
    public void drawForegroundTopLayer(final MMDGuiContainer container, final int mouseX, final int mouseY) {
        this.drawText(container); // will only be called if the correct layer is set
    }

    protected void drawText(final MMDGuiContainer container) {
        this.font.drawString(this.text, 0, 0, this.color);
    }

    @Override
    public void tick(final GuiContext context) {
        if ((this.dataWidgetKey != null) && !this.dataWidgetKey.isEmpty()) {
            final DataWidget widget = DataWidget.class.cast(context.findWidgetByKey(this.dataWidgetKey));
            if (widget != null) {
                final Object value = widget.getValue(this.dataWidgetDataKey);
                if (this.setText((value == null) ? "" : value.toString())) {
                    final MMDGuiContainer container = context.getGuiContainer();
                    if (container != null) {
                        container.initGui();
                    }
                }
            }
        }
    }
}
