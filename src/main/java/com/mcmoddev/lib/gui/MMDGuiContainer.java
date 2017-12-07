package com.mcmoddev.lib.gui;

import java.util.List;
import javax.annotation.Nullable;
import com.google.common.collect.Lists;
import com.mcmoddev.lib.container.MMDContainer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;

@SuppressWarnings("WeakerAccess")
public class MMDGuiContainer extends GuiContainer {
    protected final IGuiHolder holder;
    protected final EntityPlayer player;

    private int specifiedWidth = 0;
    private int specifiedHeight = 0;
    private int specifiedPadding = 7;

    private int piecesOffsetX = 0;
    private int piecesOffsetY = 0;

    protected List<IGuiPiece> pieces;

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

        this.holder = holder;
        this.player = player;
    }

    @Override
    public void initGui() {
        this.pieces = Lists.newArrayList();
        IGuiPieceProvider pieceProvider = this.holder.getPieceProvider();
        if (pieceProvider != null) {
            // TODO: handle the case where someone manages to return a 'null' list...
            this.pieces.addAll(pieceProvider.getPieces());
        }

        if (this.specifiedWidth > 0) {
            this.xSize = this.specifiedWidth;
        }
        else {
            int maxRight = ((this.pieces.size() > 0) || !this.inventorySlots.inventorySlots.isEmpty()) ? 0 : super.getXSize();
            int minLeft = ((this.pieces.size() > 0) || !this.inventorySlots.inventorySlots.isEmpty()) ? Integer.MAX_VALUE : 0;
            for(IGuiPiece piece: this.pieces) {
                minLeft = Math.min(minLeft, piece.getLeft());
                maxRight = Math.max(maxRight, piece.getLeft() + piece.getWidth());
            }
            for(Slot slot: this.inventorySlots.inventorySlots) {
                minLeft = Math.min(minLeft, slot.xPos);
                maxRight = Math.max(maxRight, slot.xPos + 18); // TODO: is there a constant for '18'?
            }
            this.piecesOffsetX = this.specifiedPadding - minLeft;
            this.xSize = maxRight - minLeft + this.specifiedPadding * 2;
        }

        if (this.specifiedHeight > 0) {
            this.ySize = this.specifiedHeight;
        }
        else {
            int maxBottom = ((this.pieces.size() > 0) || !this.inventorySlots.inventorySlots.isEmpty()) ? 0 : super.getYSize();
            int minTop = ((this.pieces.size() > 0) || !this.inventorySlots.inventorySlots.isEmpty()) ? Integer.MAX_VALUE : 0;
            for(IGuiPiece piece: this.pieces) {
                minTop = Math.min(minTop, piece.getTop());
                maxBottom = Math.max(maxBottom, piece.getTop() + piece.getHeight());
            }
            for(Slot slot: this.inventorySlots.inventorySlots) {
                minTop = Math.min(minTop, slot.yPos);
                maxBottom = Math.max(maxBottom, slot.yPos + 18); // TODO: find a constant for '18'?
            }
            this.piecesOffsetY = this.specifiedPadding - minTop;
            this.ySize = maxBottom - minTop + this.specifiedPadding * 2;
        }

        super.initGui();

        if ((this.piecesOffsetX > 0) || (this.piecesOffsetY > 0)) {
            for (Slot slot : this.inventorySlots.inventorySlots) {
                slot.xPos += this.piecesOffsetX + 1;
                slot.yPos += this.piecesOffsetY + 1;
            }
            this.guiLeft -= this.piecesOffsetX;
            this.guiTop -= this.piecesOffsetY;
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

        for(Slot slot : this.inventorySlots.inventorySlots) {
            GuiSprites.MC_SLOT_BACKGROUND.draw(this, slot.xPos - 1, slot.yPos - 1);
        }
    }

    protected boolean hasBackground() {
        return true;
    }

    @Nullable
    protected IGuiSprite getBackgroundSprite() {
        return null;
    }
}
