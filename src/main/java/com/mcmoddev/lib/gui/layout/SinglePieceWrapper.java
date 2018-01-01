package com.mcmoddev.lib.gui.layout;

import java.util.List;
import com.google.common.collect.Lists;
import com.mcmoddev.lib.gui.IGuiPiece;
import com.mcmoddev.lib.gui.Padding;
import com.mcmoddev.lib.gui.util.Size2D;

public class SinglePieceWrapper extends BaseLayout {
    private final IGuiPiece inner;

    public SinglePieceWrapper(IGuiPiece inner) {
        this(inner, Padding.EMPTY);
    }

    public SinglePieceWrapper(IGuiPiece inner, int uniformPadding) {
        this(inner, new Padding(uniformPadding));
    }

    public SinglePieceWrapper(IGuiPiece inner, Padding padding) {
        this.inner = inner;
        this.onChildAdded(this.inner);
        this.setPadding(padding);
    }

    @Override
    public List<IGuiPiece> getPieces() {
        return Lists.asList(this.inner, new IGuiPiece[0]);
    }

    @Override
    public Size2D getChildPosition(IGuiPiece child) {
        Padding padding = this.inner.getPadding();
        return new Size2D(padding.left, padding.top);
    }

    @Override
    public Size2D getSize() {
        Padding childPadding = this.inner.getPadding();
        Size2D childSize = this.inner.getSize();
        return new Size2D(
            childSize.width + childPadding.getHorizontal(),
            childSize.height + childPadding.getVertical()
        );
    }
}
