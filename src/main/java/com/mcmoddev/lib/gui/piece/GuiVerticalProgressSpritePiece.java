//package com.mcmoddev.lib.gui.piece;
//
//import java.util.function.Supplier;
//import com.mcmoddev.lib.container.gui.BaseSpriteGui;
//import com.mcmoddev.lib.container.gui.IGuiSprite;
//import com.mcmoddev.lib.container.gui.MMDGuiContainer;
//import com.mcmoddev.lib.container.gui.util.Size2D;
//import net.minecraftforge.fml.relauncher.Side;
//import net.minecraftforge.fml.relauncher.SideOnly;
//
//public class GuiVerticalProgressSpritePiece extends BaseSpriteGui {
//    private final Supplier<Float> loadingPercentProvider;
//
//    protected GuiVerticalProgressSpritePiece(IGuiSprite sprite,
//                                             Supplier<Float> loadingPercentProvider) {
//        super(sprite);
//        this.loadingPercentProvider = loadingPercentProvider;
//    }
//
//    protected GuiVerticalProgressSpritePiece(IGuiSprite sprite, int width, int height,
//                                             Supplier<Float> loadingPercentProvider) {
//        super(sprite, width, height);
//        this.loadingPercentProvider = loadingPercentProvider;
//    }
//
//    @Override
//    @SideOnly(Side.CLIENT)
//    public void drawMiddleLayer(MMDGuiContainer container, float partialTicks, int mouseX, int mouseY) {
//        Size2D size = this.getSize();
//
//        int loadingHeight = Math.round((float)size.height * Math.min(1.0f, Math.max(0.0f, this.loadingPercentProvider.get())));
//        loadingHeight = Math.min(size.height, Math.max(0, loadingHeight));
//
//        this.sprite.draw(container, 0, (size.height - loadingHeight),
//            0, this.sprite.getHeight() - loadingHeight, this.sprite.getWidth(), loadingHeight);
//    }
//}
