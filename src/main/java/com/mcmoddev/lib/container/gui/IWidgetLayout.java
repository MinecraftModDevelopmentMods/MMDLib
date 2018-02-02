package com.mcmoddev.lib.container.gui;

import java.util.List;
import com.mcmoddev.lib.container.gui.util.Size2D;

public interface IWidgetLayout extends IWidgetGui {
    List<IWidgetGui> getChildren();
    Size2D getChildPosition(IWidgetGui child);
}
