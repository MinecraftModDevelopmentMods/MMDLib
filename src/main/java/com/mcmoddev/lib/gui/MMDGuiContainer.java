package com.mcmoddev.lib.gui;

import javax.annotation.Nullable;
import com.mcmoddev.lib.MMDLib;
import com.mcmoddev.lib.gui.layout.BaseLayout;
import com.mcmoddev.lib.gui.util.Size2D;
import org.apache.logging.log4j.util.TriConsumer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SuppressWarnings("WeakerAccess")
public class MMDGuiContainer extends GuiContainer {
    protected final IGuiHolder holder;
    protected final EntityPlayer player;

    private int specifiedWidth = 0;
    private int specifiedHeight = 0;
    private int specifiedPadding = 7;

    private int piecesOffsetX = 0;
    private int piecesOffsetY = 0;

    protected IGuiPiece rootPiece;

    private final MMDContainer container;

    public MMDGuiContainer(IGuiHolder holder, EntityPlayer player, MMDContainer container, int padding) {
        this(holder, player, container);

        this.specifiedPadding = padding;
    }

    public MMDGuiContainer(IGuiHolder holder, EntityPlayer player, MMDContainer container, int width, int height) {
        this(holder, player, container);

        this.specifiedWidth = width;
        this.specifiedHeight = height;
    }

    public MMDGuiContainer(IGuiHolder holder, EntityPlayer player, MMDContainer container) {
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

    @Override
    public void initGui() {
        boolean firstRun = (this.rootPiece == null);

        if (firstRun) {
            IGuiPieceProvider pieceProvider = this.holder.getPieceProvider();
            Size2D rootSize = Size2D.ZERO;
            if (pieceProvider != null) {
                GuiContext context = new GuiContext(this.player, this.container, this, this.holder);
                this.rootPiece = pieceProvider.getRootPiece(context);
                Padding padding = this.rootPiece.getPadding();
                if (padding.isEmpty()) {
                    padding = new Padding(this.specifiedPadding);
                }
                rootSize = this.rootPiece.getSize().add(padding.getHorizontal(), padding.getVertical());
                this.piecesOffsetX = padding.left;
                this.piecesOffsetY = padding.top;
            } else {
                this.rootPiece = null;
            }

            this.logRootPiece();

            this.xSize = (this.specifiedWidth > 0) ? this.specifiedWidth : rootSize.width;
            this.ySize = (this.specifiedHeight > 0) ? this.specifiedHeight : rootSize.height;
        }

        super.initGui();

//        if (this.specifiedWidth > 0) {
//            this.xSize = this.specifiedWidth;
//        }
//        else {
//            int maxRight = ((this.rootPiece != null) || !this.inventorySlots.inventorySlots.isEmpty()) ? 0 : super.getXSize();
//            int minLeft = (!this.inventorySlots.inventorySlots.isEmpty()) ? Integer.MAX_VALUE : 0;
//            maxRight = Math.max(maxRight, rootSize.width);
//
//            for(Slot slot: this.inventorySlots.inventorySlots) {
//                minLeft = Math.min(minLeft, slot.xPos);
//                maxRight = Math.max(maxRight, slot.xPos + 18); // TODO: is there a constant for '18'?
//            }
//            this.piecesOffsetX = this.specifiedPadding - minLeft;
//            this.xSize = maxRight - minLeft + this.specifiedPadding * 2;
//        }
//
//        if (this.specifiedHeight > 0) {
//            this.ySize = this.specifiedHeight;
//        }
//        else {
//            int maxBottom = ((this.rootPiece != null) || !this.inventorySlots.inventorySlots.isEmpty()) ? 0 : super.getYSize();
//            int minTop = (!this.inventorySlots.inventorySlots.isEmpty()) ? Integer.MAX_VALUE : 0;
//            maxBottom = Math.max(maxBottom, rootSize.height);
//
//            for(Slot slot: this.inventorySlots.inventorySlots) {
//                minTop = Math.min(minTop, slot.yPos);
//                maxBottom = Math.max(maxBottom, slot.yPos + 18); // TODO: find a constant for '18'?
//            }
//            this.piecesOffsetY = this.specifiedPadding - minTop;
//            this.ySize = maxBottom - minTop + this.specifiedPadding * 2;
//        }
//
//        super.initGui();
//
        if (firstRun && ((this.piecesOffsetX > 0) || (this.piecesOffsetY > 0))) {
            for (Slot slot : this.inventorySlots.inventorySlots) {
                slot.xPos += this.piecesOffsetX + 1;
                slot.yPos += this.piecesOffsetY + 1;
            }
//            this.guiLeft -= this.piecesOffsetX;
//            this.guiTop -= this.piecesOffsetY;
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

    private void logPiece(@Nullable IGuiLayout layout, IGuiPiece piece, int level) {
        String prefix = new String(new char[level]).replace('\0', '\t') + "- ";
        String line = prefix + piece.getClass().getName();
        if ((layout != null) && (layout instanceof IGuiLayoutDebugInfo)) {
            line += " {" + IGuiLayoutDebugInfo.class.cast(layout).getDebugInfo(piece) + "}";
        }
        if (piece instanceof IGuiPieceDebugInfo) {
            line += " [" + IGuiPieceDebugInfo.class.cast(piece).getDebugInfo() + "]";
        }
        MMDLib.logger.info(line);
        if (piece instanceof IGuiLayout) {
            IGuiLayout container = IGuiLayout.class.cast(piece);
            for(IGuiPiece child : container.getPieces()) {
                this.logPiece(container, child, level + 1);
            }
        }
    }

    @SideOnly(Side.CLIENT)
    private void renderRootPiece(TriConsumer<IGuiPiece, Integer, Integer> renderer, int globalLeft, int globalTop, int mouseX, int mouseY) {
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
                // TODO: optimize/cache the following computations
                //#region draw default container background based on size

                // draw default background
                sprite = GuiSprites.MC_DEMO_BACKGROUND;
                int borderSize = 5;
                int overlap = 2;

                int innerWidth = sprite.getWidth() - borderSize * 2;
                int innerHeight = sprite.getHeight() - borderSize * 2;

                int innerRows = (int) Math.ceil((double) (this.ySize - (borderSize * 2)) / (double) innerHeight);
                int innerColumns = (int) Math.ceil((double) (this.xSize - (borderSize * 2)) / (double) innerWidth);

                sprite.getTexture().bind();
                for (int r = 0; r < innerRows; r++) {
                    int top = borderSize + (r * innerHeight);
                    int bottom = Math.min(top + innerHeight, this.ySize - borderSize);

                    if (bottom > top) {
                        for (int c = 0; c < innerColumns; c++) {
                            int left = borderSize + (c * innerWidth);
                            int right = Math.min(left + innerWidth, this.xSize - borderSize);

                            if (right > left) {
                                this.drawTexturedModalRect(this.guiLeft + left, this.guiTop + top, borderSize, borderSize, right - left, bottom - top);
                            }
                        }

                        this.drawTexturedModalRect(this.guiLeft, this.guiTop + top, 0, borderSize, borderSize + overlap, bottom - top);
                        this.drawTexturedModalRect(this.guiLeft + this.xSize - borderSize - overlap, this.guiTop + top, sprite.getWidth() - borderSize - overlap, borderSize, borderSize + overlap, bottom - top);
                    }
                }
                for (int c = 0; c < innerColumns; c++) {
                    int left = borderSize + (c * innerWidth);
                    int right = Math.min(left + innerWidth, this.xSize - borderSize);

                    if (right > left) {
                        this.drawTexturedModalRect(this.guiLeft + left, this.guiTop, borderSize, 0, right - left, borderSize + overlap);
                        this.drawTexturedModalRect(this.guiLeft + left, this.guiTop + this.ySize - borderSize - overlap, borderSize, sprite.getHeight() - borderSize - overlap, right - left, borderSize + overlap);
                    }
                }

                this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, borderSize + overlap, borderSize + overlap);
                this.drawTexturedModalRect(this.guiLeft, this.guiTop + this.ySize - borderSize - overlap, 0, sprite.getHeight() - borderSize - overlap, borderSize + overlap, borderSize + overlap);
                this.drawTexturedModalRect(this.guiLeft + this.xSize - borderSize - overlap, this.guiTop + this.ySize - borderSize - overlap, sprite.getWidth() - borderSize - overlap, sprite.getHeight() - borderSize - overlap, borderSize + overlap, borderSize + overlap);
                this.drawTexturedModalRect(this.guiLeft + this.xSize - borderSize - overlap, this.guiTop, sprite.getWidth() - borderSize - overlap, 0, borderSize + overlap, borderSize + overlap);

                //#endregion
            }
        }

//        if (this.rootPiece != null) {
//            Padding padding = this.rootPiece.getPadding();
//        }

        this.renderRootPiece((p, mx, my) -> {
            p.drawBackgroundLayer(this, partialTicks, mx, my);
        }, this.getRenderLeft(), this.getRenderTop(), mouseX, mouseY);

//        // TODO: move this to some pieces
//        for(Slot slot : this.inventorySlots.inventorySlots) {
//            GuiSprites.MC_SLOT_BACKGROUND.draw(this, slot.xPos - 1, slot.yPos - 1);
//        }

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
}
