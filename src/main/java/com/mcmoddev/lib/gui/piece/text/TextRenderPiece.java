//package com.mcmoddev.lib.gui.piece.text;
//
//import com.mcmoddev.lib.container.gui.GuiPieceLayer;
//import com.mcmoddev.lib.container.gui.MMDGuiContainer;
//import com.mcmoddev.lib.container.gui.util.Size2D;
//import net.minecraft.client.gui.FontRenderer;
//
//public class TextRenderPiece extends BaseGuiPiece {
//    private String text;
//    private Size2D measured = null;
//    private FontRenderer font;
//    private int color;
//
//    public TextRenderPiece(String text, FontRenderer font, GuiPieceLayer layer, int color) {
//        this.setText(text);
//        this.font = font;
//        this.setLayer(layer);
//        this.color = color;
//    }
//
//    public String getText() {
//        return this.text;
//    }
//
//    public void setText(String text) {
//        if ((this.text != null) && (this.text.compareToIgnoreCase(text) == 0)) {
//            return; // same text
//        }
//
//        this.text = text;
//        this.measured = null;
//    }
//
//    @Override
//    public Size2D getSize() {
//        if (this.measured == null) {
//            int width = this.font.getStringWidth(this.text);
//            int height = this.font.FONT_HEIGHT;
//            this.measured = new Size2D(width, height);
//        }
//
//        return this.measured;
//    }
//
//    @Override
//    public void drawBackgroundLayer(MMDGuiContainer container, float partialTicks, int mouseX, int mouseY) {
//        this.drawText(container); // will only be called if the correct layer is set
//    }
//
//    @Override
//    public void drawMiddleLayer(MMDGuiContainer container, float partialTicks, int mouseX, int mouseY) {
//        this.drawText(container); // will only be called if the correct layer is set
//    }
//
//    @Override
//    public void drawForegroundLayer(MMDGuiContainer container, int mouseX, int mouseY) {
//        this.drawText(container); // will only be called if the correct layer is set
//    }
//
//    @Override
//    public void drawForegroundTopLayer(MMDGuiContainer container, int mouseX, int mouseY) {
//        this.drawText(container); // will only be called if the correct layer is set
//    }
//
//    protected void drawText(MMDGuiContainer container) {
//        this.font.drawString(this.text, 0, 0, this.color);
//    }
//}
