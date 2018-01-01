package com.mcmoddev.lib.gui.layout;

import com.mcmoddev.lib.gui.IGuiPiece;

public class GridLayout extends BaseGridLayout {
    public GridLayout(int minColumns, int minRows) {
        super(minColumns, minRows);
    }

    public GridLayout addPiece(IGuiPiece piece, int column, int row) {
        super.addPieceInternal(piece, column, row);
        return this;
    }

    public GridLayout addPiece(IGuiPiece piece, int column, int row, int columnSpan, int rowSpan) {
        super.addPieceInternal(piece, column, row, columnSpan, rowSpan);
        return this;
    }
}
