package com.mcmoddev.lib.container.gui.layout;

import java.util.Arrays;
import javax.annotation.Nullable;
import com.mcmoddev.lib.container.gui.GuiPieceLayer;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.gui.IWidgetGuiDebugInfo;
import com.mcmoddev.lib.container.gui.IWidgetLayout;
import com.mcmoddev.lib.container.gui.MMDGuiContainer;
import com.mcmoddev.lib.container.gui.util.Padding;
import com.mcmoddev.lib.container.gui.util.Size2D;
import org.apache.logging.log4j.util.TriConsumer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SuppressWarnings("WeakerAccess")
@SideOnly(Side.CLIENT)
public abstract class BaseLayout implements IWidgetLayout, IWidgetGuiDebugInfo {
    public static final float CHILD_Z_INCREASE = 0.01f;

    private Padding padding = Padding.EMPTY;
    private boolean visible = true;
    private IWidgetLayout parentLayout = null;

    protected void onChildAdded(IWidgetGui child) {
        child.setParentLayout(this);
    }

    @Override
    public Padding getPadding() {
        return this.padding;
    }

    @Override
    public IWidgetGui setPadding(Padding value) {
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
    public IWidgetLayout getParentLayout() {
        return this.parentLayout;
    }

    @Override
    public IWidgetGui setParentLayout(@Nullable IWidgetLayout layout) {
        this.parentLayout = layout;
        return this;
    }

    @Override
    public GuiPieceLayer[] getLayers() {
        return this.getChildren()
            .stream()
            .flatMap(p -> Arrays.stream(p.getLayers()))
            .distinct()
            .toArray(GuiPieceLayer[]::new);
    }

    @Override
    public void drawBackgroundLayer(MMDGuiContainer container, float partialTicks, int mouseX, int mouseY) {
        this.getChildren().stream().filter(p -> p.rendersInLayer(GuiPieceLayer.BACKGROUND)).forEach(piece -> {
            this.renderPiece(piece, mouseX, mouseY, (p, x, y) -> {
                p.drawBackgroundLayer(container, partialTicks, x, y);
            });
        });
    }

    @Override
    public void drawMiddleLayer(MMDGuiContainer container, float partialTicks, int mouseX, int mouseY) {
        this.getChildren().stream().filter(p -> p.rendersInLayer(GuiPieceLayer.MIDDLE)).forEach(piece -> {
            this.renderPiece(piece, mouseX, mouseY, (p, x, y) -> {
                p.drawMiddleLayer(container, partialTicks, x, y);
            });
        });
    }

    @Override
    public void drawForegroundLayer(MMDGuiContainer container, int mouseX, int mouseY) {
        this.getChildren().stream().filter(p -> p.rendersInLayer(GuiPieceLayer.FOREGROUND)).forEach(piece -> {
            this.renderPiece(piece, mouseX, mouseY, (p, x, y) -> {
                p.drawForegroundLayer(container, x, y);
            });
        });
    }

    @Override
    public void drawForegroundTopLayer(MMDGuiContainer container, int mouseX, int mouseY) {
        this.getChildren().stream().filter(p -> p.rendersInLayer(GuiPieceLayer.TOP)).forEach(piece -> {
            this.renderPiece(piece, mouseX, mouseY, (p, x, y) -> {
                p.drawForegroundTopLayer(container, x, y);
            });
        });
    }

    // TODO: maybe find a better TriConsumer
    protected void renderPiece(IWidgetGui piece, int mouseX, int mouseY, TriConsumer<IWidgetGui, Integer, Integer> renderer) {
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

    @Override
    public boolean mouseClicked(MMDGuiContainer container, int mouseX, int mouseY, int mouseButton) {
        for(IWidgetGui child: this.getChildren()) {
            Size2D pos = this.getChildPosition(child);
            if ((mouseX >= pos.width) && (mouseY >= pos.height)) {
                Size2D size = child.getSize();
                int localX = mouseX - pos.width;
                int localY = mouseY - pos.height;
                if ((localX <= size.width) && (localY <= size.height)
                    && child.mouseClicked(container, localX, localY, mouseButton)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MMDGuiContainer container, int mouseX, int mouseY, int state) {
        for(IWidgetGui child: this.getChildren()) {
            Size2D pos = this.getChildPosition(child);
            if ((mouseX >= pos.width) && (mouseY >= pos.height)) {
                Size2D size = child.getSize();
                int localX = mouseX - pos.width;
                int localY = mouseY - pos.height;
                if ((localX <= size.width) && (localY <= size.height)
                    && child.mouseReleased(container, localX, localY, state)) {
                    return true;
                }
            }
        }
        return false;
    }
}
