package com.mcmoddev.lib.container.gui.layout;

import com.mcmoddev.lib.container.gui.IWidgetGui;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GridLayout extends BaseGridLayout {
    public GridLayout(int minColumns, int minRows) {
        super(minColumns, minRows);
    }

    public GridLayout addPiece(IWidgetGui piece, int column, int row) {
        super.addPieceInternal(piece, column, row);
        return this;
    }

    public GridLayout addPiece(IWidgetGui piece, int column, int row, int columnSpan, int rowSpan) {
        super.addPieceInternal(piece, column, row, columnSpan, rowSpan);
        return this;
    }
}
