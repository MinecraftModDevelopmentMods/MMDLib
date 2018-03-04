package com.mcmoddev.lib.container.gui;

import com.mcmoddev.lib.MMDLib;
import com.mcmoddev.lib.container.widget.ActionTextWidget;
import com.mcmoddev.lib.container.widget.ActionWidget;
import com.mcmoddev.lib.container.widget.DataWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

// TODO: find a way to make a base class for this and Label
public class TextEditWidgetGui extends BaseWidgetGui implements IFocusableWidgetGui {
    public static final int DEFAULT_HEIGHT = 18; // Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT + 4;

    private final GuiTextField textField;

    private String dataWidgetKey = null;
    private String dataWidgetDataKey = null;

    private String actionWidgetKey = null;

    private String lastDataWidgetText = null;
    private String lastActionWidgetText = null;

    public TextEditWidgetGui(final int par5Width) {
        super(par5Width, DEFAULT_HEIGHT);
        this.textField = new GuiTextField(0, Minecraft.getMinecraft().fontRenderer, 0, 0, par5Width, DEFAULT_HEIGHT);
    }

    public TextEditWidgetGui connectDataWidget(final String widgetKey, final String dataKey) {
        this.dataWidgetKey = widgetKey;
        this.dataWidgetDataKey = dataKey;
        return this;
    }

    public TextEditWidgetGui connectActionWidget(final String widgetKey) {
        this.actionWidgetKey = widgetKey;
        return this;
    }

    public TextEditWidgetGui setMaxLength(final int length) {
        this.textField.setMaxStringLength(length);
        return this;
    }

    public String getText() {
        return this.textField.getText();
    }

    public void setText(final String text) {
        this.textField.setText(text);
    }

    @Override
    public GuiPieceLayer[] getLayers() {
        return new GuiPieceLayer[]{ GuiPieceLayer.FOREGROUND};
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawForegroundLayer(final MMDGuiContainer container, final int mouseX, final int mouseY) {
        this.textField.drawTextBox();
    }

    private IFocusableHandler focusableHandler = null;

    @Override
    public void setFocusableHandler(final IFocusableHandler handler) {
        this.focusableHandler = handler;
    }

    @Override
    public void onFocus() {
        this.textField.setFocused(true);
    }

    @Override
    public void onBlur() {
        this.textField.setFocused(false);
    }

    @Override
    public void focus() {
        if (this.focusableHandler != null) {
            this.focusableHandler.setFocus(this);
        }
    }

    @Override
    public boolean handleKeyPress(final char typedChar, final int keyCode) {
        return this.textField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    public boolean mouseReleased(final MMDGuiContainer container, final int mouseX, final int mouseY, final int state) {
        if (this.isInside(mouseX, mouseY)) {
            this.focus();
            return true;
        }
        return false;
    }

    @Override
    public void tick(final GuiContext context) {
        if ((this.dataWidgetKey != null) && !this.dataWidgetKey.isEmpty()) {
            final DataWidget widget = DataWidget.class.cast(context.findWidgetByKey(this.dataWidgetKey));
            if (widget != null) {
                final Object rawValue = widget.getValue(this.dataWidgetDataKey);
                final String value = (rawValue == null) ? "" : rawValue.toString();
                if (!value.equals(this.lastDataWidgetText)) {
                    // data updated on widget
                    MMDLib.logger.info("Text changed on widget: " + value);
                    this.setText(value);
                    this.lastDataWidgetText = this.getText();
                } else {
                    final String text = this.getText();
                    if (!text.equals(this.lastDataWidgetText)) {
                        // data updated in text box
                        // optimistically ignore action widget
                        MMDLib.logger.info("Sending text change to data widget: " + text);
                        widget.setValue(this.dataWidgetDataKey, this.lastDataWidgetText = text);
                    }
                }
            }
        }

        if ((this.actionWidgetKey != null) && !this.actionWidgetKey.isEmpty()) {
            final String text = this.getText();

            final ActionWidget action = ActionWidget.class.cast(context.findWidgetByKey(this.actionWidgetKey));
            if ((action != null) && !text.equals(this.lastActionWidgetText)) {
                MMDLib.logger.info("Sending text change to action widget: " + text);
                action.actionPerformed(ActionTextWidget.getActionNBT(this.lastActionWidgetText = text));
            }
        }
    }
}
