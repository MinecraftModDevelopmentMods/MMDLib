package com.mcmoddev.lib.container.gui;

import com.mcmoddev.lib.MMDLib;
import com.mcmoddev.lib.container.gui.util.Size2D;
import com.mcmoddev.lib.container.gui.util.TexturedRectangleRenderer;
import com.mcmoddev.lib.container.widget.ActionWidget;
import com.mcmoddev.lib.container.widget.IWidget;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public abstract class BaseButtonWidgetGui extends BaseWidgetGui {
    public static final int STANDARD_WIDTH = 120;
    public static final int STANDARD_HEIGHT = 20;

    private String connectedWidgetKey = null;
    private Runnable clickAction = null;

    protected BaseButtonWidgetGui() {
        this(STANDARD_WIDTH, STANDARD_HEIGHT);
    }

    protected BaseButtonWidgetGui(int width, int height) {
        this(new Size2D(width, height));
    }

    protected BaseButtonWidgetGui(Size2D size) {
        super(size);
    }

    public BaseButtonWidgetGui setClickAction(Runnable action) {
        this.clickAction = action;
        return this;
    }

    public BaseButtonWidgetGui connectToWidget(String widgetKey) {
        this.connectedWidgetKey = widgetKey;
        return this;
    }

    public BaseButtonWidgetGui connectToWidget(IWidget widget) {
        return this.connectToWidget(widget.getKey());
    }

    //#region RENDER

    @Override
    public GuiPieceLayer[] getLayers() {
        return new GuiPieceLayer[] { GuiPieceLayer.BACKGROUND, GuiPieceLayer.FOREGROUND };
    }

    @Override
    public void drawBackgroundLayer(MMDGuiContainer container, float partialTicks, int mouseX, int mouseY) {
        Size2D size = this.getSize();
        TexturedRectangleRenderer.drawOnGUI(container,
            this.isInside(mouseX, mouseY) ? GuiSprites.MC_BUTTON_HOVER : GuiSprites.MC_BUTTON,
            4, size.width, size.height);
    }

    @Override
    public boolean mouseReleased(MMDGuiContainer container, int mouseX, int mouseY, int mouseButton) {
        if (!this.isInside(mouseX, mouseY)) {
            return false;
        }

        if (this.clickAction != null) {
            this.clickAction.run();
        }

        if ((this.connectedWidgetKey != null) && (this.connectedWidgetKey.length() > 0)) {
            IWidget widget = container.findWidgetByKey(this.connectedWidgetKey);
            if (widget instanceof ActionWidget) {
                ((ActionWidget)widget).actionPerformed();
            } else {
                MMDLib.logger.warn("Action Widget '" + this.connectedWidgetKey + "' not found in current container.");
            }
        }

        return true;
    }

    //#endregion
}
