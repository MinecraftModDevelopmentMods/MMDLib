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
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SuppressWarnings("unused")
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

    public MMDGuiContainer(final IWidgetContainer holder, final EntityPlayer player, final MMDContainer container, final int padding) {
        this(holder, player, container);

        this.specifiedPadding = padding;
    }

    public MMDGuiContainer(final IWidgetContainer holder, final EntityPlayer player, final MMDContainer container, final int width, final int height) {
        this(holder, player, container);

        this.specifiedWidth = width;
        this.specifiedHeight = height;
    }

    public MMDGuiContainer(final IWidgetContainer holder, final EntityPlayer player, final MMDContainer container) {
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

    public Size2D getRenderOffset(final IWidgetGui piece) {
        final Size2D initial = piece.getRenderOffset();
        return new Size2D(initial.width + this.piecesOffsetX, initial.height + this.piecesOffsetY);
    }

    @Override
    public void initGui() {
        final boolean firstRun = (this.rootPiece == null);
        final GuiContext context = new GuiContext(this.player, this.container, this, this.holder);

        final Size2D rootSize;
        if (this.rootPiece == null) {
            this.rootPiece = this.holder.getRootWidgetGui(context);
            this.focusables = new ArrayList<>();
            WidgetGuiIterable.forEach(this.rootPiece, widget -> {
                widget.init(context);
                if (widget instanceof IFocusableWidgetGui) {
                    ((IFocusableWidgetGui) widget).setFocusableHandler(this);
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
            final GuiContext context = new GuiContext(this.player, this.container, this, this.holder);
            WidgetGuiIterable.forEach(this.rootPiece, widget -> widget.tick(context));
        }
    }

    private void logRootPiece() {
        // TODO: make it a special widget gui for this
        if (this.rootPiece == null) {
            MMDLib.logger.info("GUI Opened WITHOUT a root piece.");
            return;
        }

        MMDLib.logger.info("GUI Opened with:");
        this.logPiece(null, this.rootPiece, 0);
        MMDLib.logger.info("End of GUI tree.");
    }

    private void logPiece(@Nullable final IWidgetLayout layout, final IWidgetGui piece, final int level) {
        final String prefix = new String(new char[level]).replace('\0', '\t') + "- ";
        String line = prefix + piece.getClass().getName();
        if ((layout != null) && (layout instanceof IWidgetLayoutDebugInfo)) {
            line += " {" + IWidgetLayoutDebugInfo.class.cast(layout).getDebugInfo(piece) + "}";
        }
        if (piece instanceof IWidgetGuiDebugInfo) {
            line += " [" + IWidgetGuiDebugInfo.class.cast(piece).getDebugInfo() + "]";
        }
        MMDLib.logger.info(line);
        if (piece instanceof IWidgetLayout) {
            final IWidgetLayout container = IWidgetLayout.class.cast(piece);
            for (final IWidgetGui child : container.getChildren()) {
                this.logPiece(container, child, level + 1);
            }
        }
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        FontRenderer font = null;
        ItemStack forStack = ItemStack.EMPTY;

        final List<String> tooltip = new ArrayList<>();
        if (this.mc.player.inventory.getItemStack().isEmpty()) {
            final Slot hoveredSlot = this.getSlotUnderMouse();
            if ((hoveredSlot != null) && hoveredSlot.getHasStack()) {
                forStack = hoveredSlot.getStack();
                if (!forStack.isEmpty()) {
                    tooltip.addAll(this.getItemToolTip(forStack));
                    font = forStack.getItem().getFontRenderer(forStack);
                }
            }
        }
        if (this.rootPiece != null) {
            if (this.rootPiece instanceof IWidgetLayout) {
                ((IWidgetLayout) this.rootPiece)
                    .hitTest(mouseX - this.getRenderLeft(), mouseY - this.getRenderTop())
                    .forEach(gui -> gui.getTooltip(tooltip));
            } else {
                this.rootPiece.getTooltip(tooltip);
            }
        }

        if (!tooltip.isEmpty()) {
            if (font == null) {
                font = this.fontRenderer;
            }

            if (!forStack.isEmpty()) {
                net.minecraftforge.fml.client.config.GuiUtils.preItemToolTip(forStack);
                this.drawHoveringText(tooltip, mouseX, mouseY, font);
                net.minecraftforge.fml.client.config.GuiUtils.postItemToolTip();
            } else {
                this.drawHoveringText(tooltip, mouseX, mouseY, font);
            }
        }
    }

    @SideOnly(Side.CLIENT)
    private void renderRootPiece(final TriConsumer<IWidgetGui, Integer, Integer> renderer, final int globalLeft, final int globalTop, final int mouseX, final int mouseY) {
        if (this.rootPiece != null) {
            try {
                GlStateManager.pushMatrix();
                GlStateManager.translate(globalLeft, globalTop, BaseLayout.CHILD_Z_INCREASE);
                renderer.accept(this.rootPiece, mouseX - globalLeft, mouseY - globalTop);
            } finally {
                GlStateManager.popMatrix();
            }
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(final float partialTicks, final int mouseX, final int mouseY) {
        if (this.hasBackground()) {
            final IGuiSprite sprite = this.getBackgroundSprite();
            if (sprite != null) {
                // assume the background sprite is the same size as the container
                // TODO: consider adding a 'stretch' method to sprites
                sprite.draw(this, 0, 0);
            } else {
                TexturedRectangleRenderer.drawOnGUI(this, GuiSprites.MC_DEMO_BACKGROUND, 5, 1, this.guiLeft, this.guiTop, this.xSize, this.ySize);
            }
        }

        this.renderRootPiece((p, mx, my) -> p.drawBackgroundLayer(this, partialTicks, mx, my),
            this.getRenderLeft(), this.getRenderTop(), mouseX, mouseY);

        this.renderRootPiece((p, mx, my) -> p.drawMiddleLayer(this, partialTicks, mx, my),
            this.getRenderLeft(), this.getRenderTop(), mouseX, mouseY);
    }

    protected boolean hasBackground() {
        return true;
    }

    @Nullable
    protected IGuiSprite getBackgroundSprite() {
        return null;
    }

    @Override
    protected void drawGuiContainerForegroundLayer(final int mouseX, final int mouseY) {
        super.drawGuiContainerForegroundLayer(mouseX, mouseY);

        this.renderRootPiece((p, mx, my) -> p.drawForegroundLayer(this, mx, my),
            this.piecesOffsetX, this.piecesOffsetY, mouseX - this.guiLeft, mouseY - this.guiTop);

        this.renderRootPiece((p, mx, my) -> p.drawForegroundTopLayer(this, mx, my),
            this.piecesOffsetX, this.piecesOffsetY, mouseX - this.guiLeft, mouseY - this.guiTop);
    }

    public void drawFilledRect(final int x, final int y, final int width, final int height, final int color) {
        this.drawGradientRect(x, y, x + width, y + height, color, color);
    }

    public void drawFilledRect(final int x, final int y, final int width, final int height, final int color, final int strokeColor) {
        this.drawFilledRect(x, y, width, height, color);

        this.drawHorizontalLine(x, x + width - 1, y, strokeColor);
        this.drawVerticalLine(x, y, y + height - 1, strokeColor);
        this.drawVerticalLine(x + width - 1, y, y + height - 1, strokeColor);
        this.drawHorizontalLine(x, x + width - 1, y + height - 1, strokeColor);

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    public IWidget findWidgetByKey(final String widgetKey) {
        return this.container.findWidgetByKey(widgetKey);
    }

    @Override
    protected void mouseClicked(final int mouseX, final int mouseY, final int mouseButton) throws IOException {
        final int rootX = mouseX - this.guiLeft - this.piecesOffsetX;
        final int rootY = mouseY - this.guiTop - this.piecesOffsetY;
        if ((rootX >= 0) && (rootY >= 0)) {
            final Size2D size = this.rootPiece.getSize();
            if ((rootX <= size.width) && (rootY <= size.height)
                && this.rootPiece.mouseClicked(this, rootX, rootY, mouseButton)) {
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(final int mouseX, final int mouseY, final int state) {
        final int rootX = mouseX - this.guiLeft - this.piecesOffsetX;
        final int rootY = mouseY - this.guiTop - this.piecesOffsetY;
        if ((rootX >= 0) && (rootY >= 0)) {
            final Size2D size = this.rootPiece.getSize();
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
    public void setFocus(@Nullable final IFocusableWidgetGui widgetGui) {
        if (this.currentFocus != null) {
            this.currentFocus.onBlur();
        }

        this.currentFocus = widgetGui;
        if (this.currentFocus != null) {
            this.currentFocusIndex = this.focusables.indexOf(this.currentFocus);
            this.currentFocus.onFocus();
        } else {
            this.currentFocusIndex = -1;
        }
    }

    public void clearFocus() {
        this.setFocus(null);
    }

    @Override
    protected void keyTyped(final char typedChar, final int keyCode) throws IOException {
        if ((this.currentFocus != null) && this.currentFocus.handleKeyPress(typedChar, keyCode)) {
            return; // key handled, don't call default code
        } else if ((typedChar > 0) && (keyCode == Keyboard.KEY_TAB) && (this.focusables != null) && (this.focusables.size() > 1)) {
            this.setFocus(this.focusables.get((this.currentFocusIndex + 1) % this.focusables.size()));
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }
}
