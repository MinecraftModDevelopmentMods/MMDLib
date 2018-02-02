package com.mcmoddev.lib.container.gui.layout;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.gui.util.Padding;
import com.mcmoddev.lib.container.gui.util.Size2D;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SuppressWarnings("WeakerAccess")
@SideOnly(Side.CLIENT)
public abstract class BaseGridLayout extends BaseLayout {
    // TODO: add piece align in the cell

    protected class GridPieceInfo {
        public final IWidgetGui piece;
        public final int column;
        public final int row;
        public final int columnSpan;
        public final int rowSpan;

        protected GridPieceInfo(IWidgetGui piece, int column, int row, int columnSpan, int rowSpan) {
            this.piece = piece;
            this.column = column;
            this.row = row;
            this.columnSpan = Math.max(1, columnSpan);
            this.rowSpan = Math.max(1, rowSpan);
        }
    }

    public final int minColumns;
    public final int minRows;

    private final List<GridPieceInfo> pieces = Lists.newArrayList();
    private final Map<IWidgetGui, GridPieceInfo> piecesMap = Maps.newHashMap();

    public BaseGridLayout(int minColumns, int minRows) {
        this.minColumns = minColumns;
        this.minRows = minRows;
    }

    @Override
    public List<IWidgetGui> getChildren() {
        return this.pieces.stream().map(p -> p.piece).collect(Collectors.toList());
    }

    protected <T extends IWidgetGui> T addPieceInternal(T piece, int column, int row) {
        return this.addPieceInternal(piece, column, row, 1, 1);
    }

    protected <T extends IWidgetGui> T addPieceInternal(T piece, int column, int row, int columnSpan, int rowSpan) {
        GridPieceInfo info = new GridPieceInfo(piece, column, row, columnSpan, rowSpan);
        this.pieces.add(info);
        this.piecesMap.put(info.piece, info);
        this.onChildAdded(piece);
        return piece;
    }

    @Override
    public Size2D getSize() {
        return this.getInternalSize().size;
    }

    protected GridSizeInfo getInternalSize() {
        int cellWidth = 0;
        int cellHeight = 0;
        int columns = this.minColumns;
        int rows = this.minRows;

        for(GridPieceInfo info : this.pieces) {
            Size2D size = this.getSize(info.piece);
            if (size.width > 0) {
                cellWidth = Math.max(cellWidth, size.width / info.columnSpan);
            }
            if (size.height > 0) {
                cellHeight = Math.max(cellHeight, size.height / info.rowSpan);
            }
            columns = Math.max(columns, info.column + info.columnSpan);
            rows = Math.max(rows, info.row + info.rowSpan);
        }

        return new GridSizeInfo(
            new Size2D(cellWidth * columns, cellHeight * rows),
            new Size2D(cellWidth, cellHeight),
            columns, rows);
    }

    public final class GridSizeInfo {
        public final Size2D size;
        public final Size2D cellSize;
        public final int columns;
        public final int rows;

        private GridSizeInfo(Size2D size, Size2D cellSize, int columns, int rows) {
            this.size = size;
            this.cellSize = cellSize;

            this.columns = columns;
            this.rows = rows;
        }
    }

    private Size2D getSize(IWidgetGui piece) {
        Padding padding = piece.getPadding();
        Size2D pieceSize = piece.getSize();
        return new Size2D(
            pieceSize.width + padding.left + padding.right,
            pieceSize.height + padding.top + padding.bottom);
    }

    @Override
    public Size2D getChildPosition(IWidgetGui child) {
        if (!this.piecesMap.containsKey(child)) {
            // not our piece?!?
            // TODO: consider crashing?
            return Size2D.ZERO;
        }

        GridPieceInfo info = this.piecesMap.get(child);
        GridSizeInfo sizes = this.getInternalSize();
        int left = sizes.cellSize.width * info.column;
        int top = sizes.cellSize.height * info.row;
        int width = sizes.cellSize.width * info.columnSpan;
        int height = sizes.cellSize.height * info.rowSpan;

        Size2D pieceSize = this.getSize(info.piece);

        // TODO: add more than just centered rendering
        int renderLeft = left + (width - pieceSize.width) / 2;
        int renderTop = top + (height - pieceSize.height) / 2;

        return new Size2D(renderLeft, renderTop);
    }
}
