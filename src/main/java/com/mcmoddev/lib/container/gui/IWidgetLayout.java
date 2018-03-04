package com.mcmoddev.lib.container.gui;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import com.mcmoddev.lib.container.gui.util.Size2D;

/**
 * Represents a widget that contains other widgets.
 */
public interface IWidgetLayout extends IWidgetGui {
    /**
     * Returns the list of direct child widgets.
     * @return The list of direct child widgets.
     */
    List<IWidgetGui> getChildren();

    /**
     * Returns the actual computed render position of a child widget in local coordinates.
     * @param child The child widget to get coordinates for.
     * @return The actual computed render position of the specified child widget in local coordinates.
     */
    Size2D getChildPosition(IWidgetGui child);

    /**
     * Returns all the child widgets that contain the specified coordinates.
     * @param x The x position to test for. In local coordinates.
     * @param y The y position to test for. In local coordinates.
     * @return All the child widgets that contain the specified coordinates.
     */
    default List<IWidgetGui> hitTest(final int x, final int y) {
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
