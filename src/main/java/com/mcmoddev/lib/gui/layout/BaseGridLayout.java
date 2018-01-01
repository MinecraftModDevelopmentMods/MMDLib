package com.mcmoddev.lib.gui.layout;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mcmoddev.lib.gui.IGuiPiece;
import com.mcmoddev.lib.gui.Padding;
import com.mcmoddev.lib.gui.util.Size2D;
import net.minecraft.util.Tuple;

@SuppressWarnings("WeakerAccess")
public abstract class BaseGridLayout extends BaseLayout {
    // TODO: add piece align in the cell

    protected class GridPieceInfo {
        public final IGuiPiece piece;
        public final int column;
        public final int row;
        public final int columnSpan;
        public final int rowSpan;

        protected GridPieceInfo(IGuiPiece piece, int column, int row, int columnSpan, int rowSpan) {
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
    private final Map<IGuiPiece, GridPieceInfo> piecesMap = Maps.newHashMap();

    public BaseGridLayout(int minColumns, int minRows) {
        this.minColumns = minColumns;
        this.minRows = minRows;
    }

    @Override
    public List<IGuiPiece> getPieces() {
        return this.pieces.stream().map(p -> p.piece).collect(Collectors.toList());
    }

    protected <T extends IGuiPiece> T addPieceInternal(T piece, int column, int row) {
        return this.addPieceInternal(piece, column, row, 1, 1);
    }

    protected <T extends IGuiPiece> T addPieceInternal(T piece, int column, int row, int columnSpan, int rowSpan) {
        GridPieceInfo info = new GridPieceInfo(piece, column, row, columnSpan, rowSpan);
        this.pieces.add(info);
        this.piecesMap.put(info.piece, info);
        this.onChildAdded(piece);
        return piece;
    }

    @Override
    public Size2D getSize() {
        return this.getInternalSize().getFirst();
    }

    protected Tuple<Size2D, Size2D> getInternalSize() {
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

        return new Tuple<>(
            new Size2D(cellWidth * columns, cellHeight * rows),
            new Size2D(cellWidth, cellHeight));
    }

    private Size2D getSize(IGuiPiece piece) {
        Padding padding = piece.getPadding();
        Size2D pieceSize = piece.getSize();
        return new Size2D(
            pieceSize.width + padding.left + padding.right,
            pieceSize.height + padding.top + padding.bottom);
    }

    @Override
    public Size2D getChildPosition(IGuiPiece child) {
        if (!this.piecesMap.containsKey(child)) {
            // not our piece?!?
            // TODO: consider crashing?
            return Size2D.ZERO;
        }

        GridPieceInfo info = this.piecesMap.get(child);
        Tuple<Size2D, Size2D> sizes = this.getInternalSize();
        int left = sizes.getSecond().width * info.column;
        int top = sizes.getSecond().height * info.row;
        int width = sizes.getSecond().width * info.columnSpan;
        int height = sizes.getSecond().height * info.rowSpan;

        Size2D pieceSize = this.getSize(info.piece);

        // TODO: add more than just centered rendering
        int renderLeft = left + (width - pieceSize.width) / 2;
        int renderTop = top + (height - pieceSize.height) / 2;

        return new Size2D(renderLeft, renderTop);
    }
}
