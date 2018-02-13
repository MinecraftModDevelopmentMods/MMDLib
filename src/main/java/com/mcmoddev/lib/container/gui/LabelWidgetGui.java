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

    public LabelWidgetGui(String text) {
        this(text, Minecraft.getMinecraft().fontRenderer, GuiPieceLayer.FOREGROUND, Color.black.getRGB());
    }

    public LabelWidgetGui(String text, FontRenderer font, GuiPieceLayer layer, int color) {
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

    public boolean setText(String text) {
        if ((this.text != null) && (this.text.compareTo(text) == 0)) {
            return false; // same text
        }

        this.text = text;
        this.measured = null;
        return true;
    }

    public LabelWidgetGui connectDataWidget(String widgetKey, String dataKey) {
        this.dataWidgetKey = widgetKey;
        this.dataWidgetDataKey = dataKey;
        return this;
    }

    @Override
    public Size2D getSize() {
        if (this.measured == null) {
            int width = this.font.getStringWidth(this.text);
            int height = this.font.FONT_HEIGHT;
            this.measured = new Size2D(width, height);
        }

        return this.measured;
    }

    @Override
    public void drawBackgroundLayer(MMDGuiContainer container, float partialTicks, int mouseX, int mouseY) {
        this.drawText(container); // will only be called if the correct layer is set
    }

    @Override
    public void drawMiddleLayer(MMDGuiContainer container, float partialTicks, int mouseX, int mouseY) {
        this.drawText(container); // will only be called if the correct layer is set
    }

    @Override
    public void drawForegroundLayer(MMDGuiContainer container, int mouseX, int mouseY) {
        this.drawText(container); // will only be called if the correct layer is set
    }

    @Override
    public void drawForegroundTopLayer(MMDGuiContainer container, int mouseX, int mouseY) {
        this.drawText(container); // will only be called if the correct layer is set
    }

    protected void drawText(MMDGuiContainer container) {
        this.font.drawString(this.text, 0, 0, this.color);
    }

    @Override
    public void tick(GuiContext context) {
        if ((this.dataWidgetKey != null) && !this.dataWidgetKey.isEmpty()) {
            DataWidget widget = DataWidget.class.cast(context.findWidgetByKey(this.dataWidgetKey));
            if (widget != null) {
                Object value = widget.getValue(this.dataWidgetDataKey);
                if (this.setText((value == null) ? "" : value.toString())) {
                    MMDGuiContainer container = context.getGuiContainer();
                    if (container != null) {
                        container.initGui();
                    }
                }
            }
        }
    }
}
