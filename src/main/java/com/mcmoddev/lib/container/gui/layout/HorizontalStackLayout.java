package com.mcmoddev.lib.container.gui.layout;

import java.util.List;
import com.google.common.collect.Lists;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.gui.IWidgetLayoutDebugInfo;
import com.mcmoddev.lib.container.gui.util.Padding;
import com.mcmoddev.lib.container.gui.util.Size2D;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class HorizontalStackLayout extends BaseLayout implements IWidgetLayoutDebugInfo {
    private final List<IWidgetGui> pieces = Lists.newArrayList();

    public HorizontalStackLayout() {
    }

    @Override
    public List<IWidgetGui> getChildren() {
        return Lists.newArrayList(this.pieces);
    }

    public HorizontalStackLayout addPiece(IWidgetGui piece) {
        this.pieces.add(piece);
        this.onChildAdded(piece);
        return this;
    }

    private Size2D getSize(IWidgetGui piece) {
        Padding padding = piece.getPadding();
        Size2D pieceSize = piece.getSize();
        return new Size2D(
            pieceSize.width + padding.left + padding.right,
            pieceSize.height + padding.top + padding.bottom);
    }

    @Override
    public Size2D getSize() {
        int width = 0;
        int height = 0;

        for(IWidgetGui piece : this.pieces) {
            Size2D pieceSize = this.getSize(piece);
            width += pieceSize.width;
            height = Math.max(height, pieceSize.height);
        }
        return new Size2D(width, height);
    }

    @Override
    public Size2D getChildPosition(IWidgetGui child) {
        int height = 0;
        int left = 0;
        boolean found = false;

        for(IWidgetGui tempPiece : this.pieces) {
            Size2D pieceSize = this.getSize(tempPiece);
            height = Math.max(height, pieceSize.height);

            if (child == tempPiece) {
                found = true;
            }

            if (!found) {
                left += pieceSize.width;
            }
        }

        Padding padding = child.getPadding();
        int pieceHeight = padding.top + padding.bottom + child.getSize().height;
        int top = (height - pieceHeight) / 2 + padding.top;
        left += padding.left;

        return new Size2D(left, top);
    }

    @Override
    public String getDebugInfo(IWidgetGui child) {
        Size2D pos = this.getChildPosition(child);
        return String.format("i: %d, cx: %d, cy: %d", this.pieces.indexOf(child), pos.width, pos.height);
    }
}
