package com.mcmoddev.lib.gui;

import java.util.Deque;
import java.util.Iterator;
import com.google.common.collect.Queues;

public class GuiPieceIterator implements Iterable<IGuiPiece>, Iterator<IGuiPiece> {
    private final Deque<IGuiPiece> pieces = Queues.newArrayDeque();

    public GuiPieceIterator(IGuiPiece root) {
        this.pieces.push(root);
    }

    @Override
    public boolean hasNext() {
        return (this.pieces.size() > 0);
    }

    @Override
    public IGuiPiece next() {
        IGuiPiece piece = this.pieces.pop();

        if (piece instanceof IGuiLayout) {
            IGuiLayout layout = IGuiLayout.class.cast(piece);
            for(IGuiPiece child : layout.getPieces()) {
                this.pieces.push(child);
            }
        }

        return piece;
    }

    @Override
    public Iterator<IGuiPiece> iterator() {
        return this;
    }
}
