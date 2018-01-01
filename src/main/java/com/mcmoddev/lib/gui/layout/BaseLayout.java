package com.mcmoddev.lib.gui.layout;

import java.util.Arrays;
import javax.annotation.Nullable;
import com.mcmoddev.lib.gui.GuiPieceLayer;
import com.mcmoddev.lib.gui.IGuiLayout;
import com.mcmoddev.lib.gui.IGuiPiece;
import com.mcmoddev.lib.gui.IGuiPieceDebugInfo;
import com.mcmoddev.lib.gui.MMDGuiContainer;
import com.mcmoddev.lib.gui.Padding;
import com.mcmoddev.lib.gui.util.Size2D;
import org.apache.logging.log4j.util.TriConsumer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SuppressWarnings("WeakerAccess")
public abstract class BaseLayout implements IGuiLayout, IGuiPieceDebugInfo {
    public static final float CHILD_Z_INCREASE = 0.01f;

    private Padding padding = Padding.EMPTY;
    private boolean visible = true;
    private IGuiLayout parentLayout = null;

    protected void onChildAdded(IGuiPiece child) {
        child.setParentLayout(this);
    }

    @Override
    public Padding getPadding() {
        return this.padding;
    }

    @Override
    public IGuiPiece setPadding(Padding value) {
        this.padding = value;
        return this;
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }

    @Override
    public boolean setVisibility(boolean isVisible) {
        if (this.canChangeVisibility()) {
            this.visible = isVisible;
        }

        return this.visible;
    }

    protected boolean canChangeVisibility() {
        return true;
    }

    @Nullable
    @Override
    public IGuiLayout getParentLayout() {
        return this.parentLayout;
    }

    @Override
    public IGuiPiece setParentLayout(@Nullable IGuiLayout layout) {
        this.parentLayout = layout;
        return this;
    }

    @Override
    public GuiPieceLayer[] getLayers() {
        return this.getPieces()
            .stream()
            .flatMap(p -> Arrays.stream(p.getLayers()))
            .distinct()
            .toArray(GuiPieceLayer[]::new);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawBackgroundLayer(MMDGuiContainer container, float partialTicks, int mouseX, int mouseY) {
        this.getPieces().stream().filter(p -> p.rendersInLayer(GuiPieceLayer.BACKGROUND)).forEach(piece -> {
            this.renderPiece(piece, mouseX, mouseY, (p, x, y) -> {
                p.drawBackgroundLayer(container, partialTicks, x, y);
            });
        });
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawMiddleLayer(MMDGuiContainer container, float partialTicks, int mouseX, int mouseY) {
        this.getPieces().stream().filter(p -> p.rendersInLayer(GuiPieceLayer.MIDDLE)).forEach(piece -> {
            this.renderPiece(piece, mouseX, mouseY, (p, x, y) -> {
                p.drawMiddleLayer(container, partialTicks, x, y);
            });
        });
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawForegroundLayer(MMDGuiContainer container, int mouseX, int mouseY) {
        this.getPieces().stream().filter(p -> p.rendersInLayer(GuiPieceLayer.FOREGROUND)).forEach(piece -> {
            this.renderPiece(piece, mouseX, mouseY, (p, x, y) -> {
                p.drawForegroundLayer(container, x, y);
            });
        });
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawForegroundTopLayer(MMDGuiContainer container, int mouseX, int mouseY) {
        this.getPieces().stream().filter(p -> p.rendersInLayer(GuiPieceLayer.TOP)).forEach(piece -> {
            this.renderPiece(piece, mouseX, mouseY, (p, x, y) -> {
                p.drawForegroundTopLayer(container, x, y);
            });
        });
    }

    // TODO: maybe find a better TriConsumer
    @SideOnly(Side.CLIENT)
    protected void renderPiece(IGuiPiece piece, int mouseX, int mouseY, TriConsumer<IGuiPiece, Integer, Integer> renderer) {
        try {
            GlStateManager.pushMatrix();

            Size2D offset = this.getChildPosition(piece);
            GlStateManager.translate((float)offset.width, (float)offset.height, BaseLayout.CHILD_Z_INCREASE);
            renderer.accept(piece, mouseX - offset.width, mouseY - offset.height);
        }
        finally {
            GlStateManager.popMatrix();
        }
    }

    @Override
    public String getDebugInfo() {
        Size2D size = this.getSize();
        return String.format("[cw: %d, ch: %d]", size.width, size.height);
    }
}
