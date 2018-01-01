package com.mcmoddev.lib.gui;

import java.util.List;
import com.mcmoddev.lib.gui.util.Size2D;

public interface IGuiLayout extends IGuiPiece {
    List<IGuiPiece> getPieces();

    Size2D getChildPosition(IGuiPiece child);
}
