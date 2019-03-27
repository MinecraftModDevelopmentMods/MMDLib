package com.mcmoddev.lib.container.gui.layout;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.gui.IWidgetLayoutDebugInfo;
import com.mcmoddev.lib.container.gui.util.Padding;
import com.mcmoddev.lib.container.gui.util.Size2D;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public abstract class BaseGridLayout extends BaseLayout implements IWidgetLayoutDebugInfo {
    // TODO: add piece align in the cell

    protected class GridPieceInfo {
        public final IWidgetGui piece;
        public final int column;
        public final int row;
        public final int columnSpan;
        public final int rowSpan;

        protected GridPieceInfo(final IWidgetGui piece, final int column, final int row, final int columnSpan, final int rowSpan) {
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

    public BaseGridLayout(final int minColumns, final int minRows) {
        this.minColumns = minColumns;
        this.minRows = minRows;
    }

    @Override
    public List<IWidgetGui> getChildren() {
        return this.pieces.stream().map(p -> p.piece).collect(Collectors.toList());
    }

    protected <T extends IWidgetGui> T addPieceInternal(final T piece, final int column, final int row) {
        return this.addPieceInternal(piece, column, row, 1, 1);
    }

    protected <T extends IWidgetGui> T addPieceInternal(final T piece, final int column, final int row, final int columnSpan, final int rowSpan) {
        final GridPieceInfo info = new GridPieceInfo(piece, column, row, columnSpan, rowSpan);
        this.pieces.add(info);
        this.piecesMap.put(info.piece, info);
        this.onChildAdded(piece);
        return piece;
    }

    protected <T extends IWidgetGui> T updatePieceInternal(final T piece, final int column, final int row, final int columnSpan, final int rowSpan) {
        final GridPieceInfo info = this.piecesMap.get(piece);
        if (info != null) {
            this.pieces.remove(info);
            this.piecesMap.remove(piece);
        }
        return this.addPieceInternal(piece, column, row, columnSpan, rowSpan);
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

        for(final GridPieceInfo info : this.pieces) {
            final Size2D size = this.getSize(info.piece);
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

        private GridSizeInfo(final Size2D size, final Size2D cellSize, final int columns, final int rows) {
            this.size = size;
            this.cellSize = cellSize;

            this.columns = columns;
            this.rows = rows;
        }
    }

    private Size2D getSize(final IWidgetGui piece) {
        final Padding padding = piece.getPadding();
        final Size2D pieceSize = piece.getSize();
        return new Size2D(
            pieceSize.width + padding.left + padding.right,
            pieceSize.height + padding.top + padding.bottom);
    }

    @Override
    public Size2D getChildPosition(final IWidgetGui child) {
        if (!this.piecesMap.containsKey(child)) {
            // not our piece?!?
            // TODO: consider crashing?
            return Size2D.ZERO;
        }

        final GridPieceInfo info = this.piecesMap.get(child);
        final GridSizeInfo sizes = this.getInternalSize();
        final int left = sizes.cellSize.width * info.column;
        final int top = sizes.cellSize.height * info.row;
        final int width = sizes.cellSize.width * info.columnSpan;
        final int height = sizes.cellSize.height * info.rowSpan;

        final Size2D pieceSize = this.getSize(info.piece);

        // TODO: add more than just centered rendering
        final int renderLeft = left + (width - pieceSize.width) / 2;
        final int renderTop = top + (height - pieceSize.height) / 2;

        return new Size2D(renderLeft, renderTop);
    }

    @Override
    public String getDebugInfo(final IWidgetGui child) {
        final GridPieceInfo info = this.piecesMap.get(child);
        return (info == null) ? "{no info}"
            : String.format("c: %d, r: %d, cs: %d, rs: %d", info.column, info.row, info.columnSpan, info.rowSpan);
    }
}
