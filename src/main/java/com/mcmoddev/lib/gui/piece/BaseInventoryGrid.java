package com.mcmoddev.lib.gui.piece;

import java.util.function.Function;
import java.util.function.Supplier;
import com.mcmoddev.lib.gui.layout.BaseGridLayout;
import com.mcmoddev.lib.gui.util.Size2D;
import net.minecraft.util.Tuple;

public abstract class BaseInventoryGrid extends BaseGridLayout {
    private ColorOverlayPiece overlayPiece = null;

    protected BaseInventoryGrid(int columns,
                                Supplier<Integer> slotsGetter,
                                Function<Integer, BaseInventorySlot> slotGetter) {
        super(1, 1);

        int slots = slotsGetter.get();
        for(int i = 0; i < slots; i++) {
            BaseInventorySlot slot = slotGetter.apply(i);
            super.addPieceInternal(slot, i % columns, i / columns);
        }
    }

//    public BaseInventoryGrid removeColorOverlay() {
//        this.overlayPiece = null;
//        return this;
//    }

    public BaseInventoryGrid setColorOverlay(int color) {
        Tuple<Size2D, Size2D> sizes = this.getInternalSize();

        this.overlayPiece = new ColorOverlayPiece(sizes.getFirst().width, sizes.getFirst().height, color);
        this.addPieceInternal(this.overlayPiece, 0, 0,
            sizes.getFirst().width / sizes.getSecond().width,
            sizes.getFirst().height / sizes.getSecond().height);
        return this;
    }

    public BaseInventoryGrid setColorOverlay(int color, int alpha) {
        Tuple<Size2D, Size2D> sizes = this.getInternalSize();

        this.overlayPiece = new ColorOverlayPiece(sizes.getFirst().width, sizes.getFirst().height, color, alpha);
        this.addPieceInternal(this.overlayPiece, 0, 0,
            sizes.getFirst().width / sizes.getSecond().width,
            sizes.getFirst().height / sizes.getSecond().height);
        return this;
    }
}
