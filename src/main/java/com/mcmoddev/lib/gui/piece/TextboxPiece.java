//package com.mcmoddev.lib.gui.piece;
//
//import java.awt.Color;
//import com.mcmoddev.lib.container.gui.GuiPieceLayer;
//import com.mcmoddev.lib.container.gui.MMDGuiContainer;
//import com.mcmoddev.lib.container.gui.util.Size2D;
//import net.minecraftforge.fml.relauncher.Side;
//import net.minecraftforge.fml.relauncher.SideOnly;
//
//public class TextboxPiece extends BaseGuiPiece {
//    public TextboxPiece(int width) {
//        this(width, 1);
//    }
//
//    public TextboxPiece(int width, int textLines) {
//        super(width, 2 + textLines * 9 /* Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT */);
//    }
//
//    @Override
//    public GuiPieceLayer[] getLayers() {
//        return new GuiPieceLayer[] { GuiPieceLayer.BACKGROUND, GuiPieceLayer.FOREGROUND };
//    }
//
//    @Override
//    @SideOnly(Side.CLIENT)
//    public void drawBackgroundLayer(MMDGuiContainer container, float partialTicks, int mouseX, int mouseY) {
//        Size2D size = this.getSize();
//        container.drawFilledRect(0, 0, size.width, size.height, Color.GRAY.getRGB(), Color.BLACK.getRGB());
//    }
//
//    @Override
//    @SideOnly(Side.CLIENT)
//    public void drawForegroundLayer(MMDGuiContainer container, int mouseX, int mouseY) {
//
//    }
//}
