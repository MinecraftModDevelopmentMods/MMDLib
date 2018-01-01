package com.mcmoddev.lib.gui.layout;

import java.util.List;
import com.google.common.collect.Lists;
import com.mcmoddev.lib.gui.IGuiLayoutDebugInfo;
import com.mcmoddev.lib.gui.IGuiPiece;
import com.mcmoddev.lib.gui.Padding;
import com.mcmoddev.lib.gui.util.Size2D;

public class VerticalStackLayout extends BaseLayout implements IGuiLayoutDebugInfo {
    private final List<IGuiPiece> pieces = Lists.newArrayList();

    public VerticalStackLayout() {
    }

    @Override
    public List<IGuiPiece> getPieces() {
        return Lists.newArrayList(this.pieces);
    }

    public VerticalStackLayout addPiece(IGuiPiece piece) {
        this.pieces.add(piece);
        this.onChildAdded(piece);
        return this;
    }

    private Size2D getSize(IGuiPiece piece) {
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

        for(IGuiPiece piece : this.pieces) {
            Size2D pieceSize = this.getSize(piece);
            width = Math.max(width, pieceSize.width);
            height += pieceSize.height;
        }
        return new Size2D(width, height);
    }

    @Override
    public Size2D getChildPosition(IGuiPiece child) {
        int width = 0;
        int top = 0;
        boolean found = false;

        for(IGuiPiece tempPiece : this.pieces) {
            Size2D pieceSize = this.getSize(tempPiece);
            width = Math.max(width, pieceSize.width);

            if (child == tempPiece) {
                found = true;
            }

            if (!found) {
                top += pieceSize.height;
            }
        }

        if (!found) {
            // TODO: maybe crash?
            return Size2D.ZERO;
        }

        Padding padding = child.getPadding();
        int pieceWidth = padding.left + padding.right + child.getSize().width;
        int left = (width - pieceWidth) / 2 + padding.left;
        top += padding.top;

        return new Size2D(left, top);
    }

    @Override
    public String getDebugInfo(IGuiPiece piece) {
        Size2D pos = this.getChildPosition(piece);
        return String.format("i: %d, cx: %d, cy: %d", this.pieces.indexOf(piece), pos.width, pos.height);
    }
}
