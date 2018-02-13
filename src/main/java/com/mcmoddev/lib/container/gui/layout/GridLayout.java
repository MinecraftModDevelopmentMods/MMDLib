package com.mcmoddev.lib.container.gui.layout;

import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.gui.IWidgetLayoutDebugInfo;
import com.mcmoddev.lib.container.gui.util.Size2D;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GridLayout extends BaseGridLayout implements IWidgetLayoutDebugInfo {
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

    @Override
    public String getDebugInfo() {
        GridSizeInfo size = this.getInternalSize();
        return super.getDebugInfo() + String.format(", columns: %d, rows: %d", size.columns, size.rows);
    }

    @Override
    public String getDebugInfo(IWidgetGui piece) {
        Size2D pos = this.getChildPosition(piece);
        GridSizeInfo size = this.getInternalSize();
        return String.format("col: %d, row: %d", pos.width / size.cellSize.width, pos.height / size.cellSize.height);
    }
}
