package com.mcmoddev.lib.container.gui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import com.mcmoddev.lib.MMDLib;
import com.mcmoddev.lib.container.IWidgetContainer;
import com.mcmoddev.lib.container.MMDContainer;
import com.mcmoddev.lib.container.gui.layout.BaseLayout;
import com.mcmoddev.lib.container.gui.util.Padding;
import com.mcmoddev.lib.container.gui.util.Size2D;
import com.mcmoddev.lib.container.gui.util.TexturedRectangleRenderer;
import com.mcmoddev.lib.container.gui.util.WidgetGuiIterable;
import com.mcmoddev.lib.container.widget.IWidget;
import org.apache.logging.log4j.util.TriConsumer;
import org.lwjgl.input.Keyboard;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SuppressWarnings("WeakerAccess")
public class MMDGuiContainer extends GuiContainer implements IFocusableHandler {
    protected final IWidgetContainer holder;
    protected final EntityPlayer player;

    private int specifiedWidth = 0;
    private int specifiedHeight = 0;
    private int specifiedPadding = 7;

    private int piecesOffsetX = 0;
    private int piecesOffsetY = 0;

    protected IWidgetGui rootPiece;

    private final MMDContainer container;

    private IFocusableWidgetGui currentFocus = null;
    private List<IFocusableWidgetGui> focusables = null;
    private int currentFocusIndex = -1;

    public MMDGuiContainer(IWidgetContainer holder, EntityPlayer player, MMDContainer container, int padding) {
        this(holder, player, container);

        this.specifiedPadding = padding;
    }

    public MMDGuiContainer(IWidgetContainer holder, EntityPlayer player, MMDContainer container, int width, int height) {
        this(holder, player, container);

        this.specifiedWidth = width;
        this.specifiedHeight = height;
    }

    public MMDGuiContainer(IWidgetContainer holder, EntityPlayer player, MMDContainer container) {
        super(container);

        this.container = container;
        this.holder = holder;
        this.player = player;
    }

    public int getRenderLeft() {
        return this.piecesOffsetX + this.getGuiLeft();
    }

    public int getRenderTop() {
        return this.piecesOffsetY + this.getGuiTop();
    }

    public Size2D getRenderOffset(IWidgetGui piece) {
        Size2D initial = piece.getRenderOffset();
        return new Size2D(initial.width + this.piecesOffsetX, initial.height + this.piecesOffsetY);
    }

    @Override
    public void initGui() {
        boolean firstRun = (this.rootPiece == null);
        GuiContext context = new GuiContext(this.player, this.container, this, this.holder);

        Size2D rootSize;
        if (this.rootPiece == null) {
            this.rootPiece = this.holder.getRootWidgetGui(context);
            this.focusables = new ArrayList<>();
            WidgetGuiIterable.forEach(this.rootPiece, widget -> {
                widget.init(context);
                if (widget instanceof IFocusableWidgetGui) {
                    ((IFocusableWidgetGui)widget).setFocusableHandler(this);
                    this.focusables.add((IFocusableWidgetGui) widget);
                }
            });
            if (this.focusables.size() > 0) {
                this.setFocus(this.focusables.get(this.currentFocusIndex = 0));
            }
        }

        Padding padding = this.rootPiece.getPadding();
        if (padding.isEmpty()) {
            padding = new Padding(this.specifiedPadding);
        }
        rootSize = this.rootPiece.getSize().add(padding.getHorizontal(), padding.getVertical());
        this.piecesOffsetX = padding.left;
        this.piecesOffsetY = padding.top;

        this.logRootPiece();

        this.xSize = (this.specifiedWidth > 0) ? this.specifiedWidth : rootSize.width;
        this.ySize = (this.specifiedHeight > 0) ? this.specifiedHeight : rootSize.height;

        super.initGui();

        if (this.rootPiece != null) {
            WidgetGuiIterable.forEach(this.rootPiece, widget -> widget.postInit(context));
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();

        if (this.rootPiece != null) {
            GuiContext context = new GuiContext(this.player, this.container, this, this.holder);
            WidgetGuiIterable.forEach(this.rootPiece, widget -> widget.tick(context));
        }
    }

    private void logRootPiece() {
        if (this.rootPiece == null) {
            MMDLib.logger.info("GUI Opened WITHOUT a root piece.");
            return;
        }

        MMDLib.logger.info("GUI Opened with:");
        this.logPiece(null, this.rootPiece, 0);
        MMDLib.logger.info("End of GUI tree.");
    }

    private void logPiece(@Nullable IWidgetLayout layout, IWidgetGui piece, int level) {
        String prefix = new String(new char[level]).replace('\0', '\t') + "- ";
        String line = prefix + piece.getClass().getName();
        if ((layout != null) && (layout instanceof IWidgetLayoutDebugInfo)) {
            line += " {" + IWidgetLayoutDebugInfo.class.cast(layout).getDebugInfo(piece) + "}";
        }
        if (piece instanceof IWidgetGuiDebugInfo) {
            line += " [" + IWidgetGuiDebugInfo.class.cast(piece).getDebugInfo() + "]";
        }
        MMDLib.logger.info(line);
        if (piece instanceof IWidgetLayout) {
            IWidgetLayout container = IWidgetLayout.class.cast(piece);
            for(IWidgetGui child : container.getChildren()) {
                this.logPiece(container, child, level + 1);
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @SideOnly(Side.CLIENT)
    private void renderRootPiece(TriConsumer<IWidgetGui, Integer, Integer> renderer, int globalLeft, int globalTop, int mouseX, int mouseY) {
        if (this.rootPiece != null) {
            try {
                GlStateManager.pushMatrix();
                GlStateManager.translate(globalLeft, globalTop, BaseLayout.CHILD_Z_INCREASE);
                renderer.accept(this.rootPiece, mouseX - globalLeft, mouseY - globalTop);
            }
            finally {
                GlStateManager.popMatrix();
            }
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        if (this.hasBackground()) {
            IGuiSprite sprite = this.getBackgroundSprite();
            if (sprite != null) {
                // assume the background sprite is the same size as the container
                // TODO: consider adding a 'stretch' method to sprites
                sprite.draw(this, 0, 0);
            } else {
                TexturedRectangleRenderer.drawOnGUI(this, GuiSprites.MC_DEMO_BACKGROUND,5, 1, this.guiLeft, this.guiTop, this.xSize, this.ySize);
            }
        }

        this.renderRootPiece((p, mx, my) -> {
            p.drawBackgroundLayer(this, partialTicks, mx, my);
        }, this.getRenderLeft(), this.getRenderTop(), mouseX, mouseY);

        this.renderRootPiece((p, mx, my) -> {
            p.drawMiddleLayer(this, partialTicks, mx, my);
        }, this.getRenderLeft(), this.getRenderTop(), mouseX, mouseY);
    }

    protected boolean hasBackground() {
        return true;
    }

    @Nullable
    protected IGuiSprite getBackgroundSprite() {
        return null;
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        super.drawGuiContainerForegroundLayer(mouseX, mouseY);

        this.renderRootPiece((p, mx, my) -> {
            p.drawForegroundLayer(this, mx, my);
        }, this.piecesOffsetX, this.piecesOffsetY, mouseX, mouseY);

        this.renderRootPiece((p, mx, my) -> {
            p.drawForegroundTopLayer(this, mx, my);
        }, this.piecesOffsetX, this.piecesOffsetY, mouseX, mouseY);
    }

    public void drawFilledRect(int x, int y, int width, int height, int color) {
        this.drawGradientRect(x, y, x + width, y + height, color, color);
    }

    public void drawFilledRect(int x, int y, int width, int height, int color, int strokeColor) {
        this.drawFilledRect(x, y, width, height, color);

        this.drawHorizontalLine(x, x + width - 1, y, strokeColor);
        this.drawVerticalLine(x, y, y + height - 1, strokeColor);
        this.drawVerticalLine(x + width - 1, y, y + height - 1, strokeColor);
        this.drawHorizontalLine(x, x + width - 1, y + height - 1, strokeColor);

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    public IWidget findWidgetByKey(String widgetKey) {
        return this.container.findWidgetByKey(widgetKey);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        int rootX = mouseX - this.guiLeft - this.piecesOffsetX;
        int rootY = mouseY - this.guiTop - this.piecesOffsetY;
        if ((rootX >= 0) && (rootY >= 0)) {
            Size2D size = this.rootPiece.getSize();
            if ((rootX <= size.width) && (rootY <= size.height)
                && this.rootPiece.mouseClicked(this, rootX, rootY, mouseButton)) {
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        int rootX = mouseX - this.guiLeft - this.piecesOffsetX;
        int rootY = mouseY - this.guiTop - this.piecesOffsetY;
        if ((rootX >= 0) && (rootY >= 0)) {
            Size2D size = this.rootPiece.getSize();
            if ((rootX <= size.width) && (rootY <= size.height)
                && this.rootPiece.mouseReleased(this, rootX, rootY, state)) {
                return;
            }
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Nullable
    @Override
    public IFocusableWidgetGui getCurrentFocus() {
        return this.currentFocus;
    }

    @Override
    public void setFocus(@Nullable IFocusableWidgetGui widgetGui) {
        if (this.currentFocus != null) {
            this.currentFocus.onBlur();
        }

        this.currentFocus = widgetGui;
        if (this.currentFocus != null) {
            this.currentFocusIndex = this.focusables.indexOf(this.currentFocus);
            this.currentFocus.onFocus();
        }
        else {
            this.currentFocusIndex = -1;
        }
    }

    public void clearFocus() {
        this.setFocus(null);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.currentFocus.handleKeyPress(typedChar, keyCode)) {
            return; // key handled, don't call default code
        }
        else if ((typedChar > 0) && (keyCode == Keyboard.KEY_TAB)) {
            // tab pressed, move next
            if ((this.focusables != null) && (this.focusables.size() > 1)) {
                this.setFocus(this.focusables.get((this.currentFocusIndex + 1) % this.focusables.size()));
                return;
            }
        }

        super.keyTyped(typedChar, keyCode);
    }
}
