package com.mcmoddev.lib.container.gui.layout;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.gui.IWidgetLayoutDebugInfo;
import com.mcmoddev.lib.container.gui.util.Size2D;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class CanvasLayout extends BaseLayout implements IWidgetLayoutDebugInfo {
    private class CanvasPieceInfo {
        public final IWidgetGui piece;
        public final int left;
        public final int top;

        private CanvasPieceInfo(IWidgetGui piece, int left, int top) {
            this.piece = piece;
            this.left = left;
            this.top = top;
        }
    }

    private final List<CanvasPieceInfo> pieces = Lists.newArrayList();
    private final Map<IWidgetGui, CanvasPieceInfo> piecesMap = Maps.newHashMap();

    @Override
    public List<IWidgetGui> getChildren() {
        return this.pieces.stream().map(p -> p.piece).collect(Collectors.toList());
    }

    public <T extends IWidgetGui> T addPiece(T piece, int left, int top) {
        CanvasPieceInfo info = new CanvasPieceInfo(piece, left, top);
        this.pieces.add(info);
        this.piecesMap.put(piece, info);
        this.onChildAdded(piece);
        return piece;
    }

    @Override
    public Size2D getSize() {
        int width = 0;
        int height = 0;

        for (CanvasPieceInfo piece : this.pieces) {
            Size2D pieceSize = piece.piece.getSize();
            width = Math.max(width, piece.left + pieceSize.width);
            height = Math.max(height, piece.top + pieceSize.height);
        }

        return new Size2D(width, height);
    }

    @Override
    public Size2D getChildPosition(IWidgetGui child) {
        if (!this.piecesMap.containsKey(child)) {
            // not our piece?!?
            // TODO: consider crashing?
            return Size2D.ZERO;
        }

        CanvasPieceInfo info = this.piecesMap.get(child);
        return new Size2D(info.left, info.top);
    }

    @Override
    public String getDebugInfo(IWidgetGui child) {
        if (!this.piecesMap.containsKey(child)) {
            return "[n/a]";
        }

        CanvasPieceInfo info = this.piecesMap.get(child);
        return String.format("x: %d, y: %d", info.left, info.top);
    }
}
