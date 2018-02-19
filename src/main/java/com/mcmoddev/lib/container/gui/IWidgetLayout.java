package com.mcmoddev.lib.container.gui;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import com.mcmoddev.lib.container.gui.util.Size2D;

public interface IWidgetLayout extends IWidgetGui {
    List<IWidgetGui> getChildren();
    Size2D getChildPosition(IWidgetGui child);

    default List<IWidgetGui> hitTest(int x, int y) {
        return this.getChildren()
            .stream()
            .filter(child -> {
                Size2D pos = this.getChildPosition(child);
                Size2D size = child.getSize();
                return ((pos.width <= x) && ((pos.width + size.width) > x) && (pos.height <= y) && ((pos.height + size.height) > y));
            })
            .flatMap(child -> {
                if (child instanceof IWidgetLayout) {
                    Size2D pos = this.getChildPosition(child);
                    List<IWidgetGui> children = ((IWidgetLayout)child).hitTest(x - pos.width, y - pos.height);
                    children.add(0, child);
                    return children.stream();
                } else {
                    return Arrays.stream(new IWidgetGui[] { child });
                }
            })
            .collect(Collectors.toList());
    }
}
