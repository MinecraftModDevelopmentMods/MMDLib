package zone.moddev.mc.lib.gui.layout;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import zone.moddev.mc.lib.gui.model.Element;

/** Width-constrained rows/stacks. Children declare their size and whether they share row width. */
public record FlowLayout(Map<Element, Rect> children, int height) {
    public static FlowLayout of(
        List<? extends Element> children, boolean horizontal, int width, int gap,
        TextMeasure text) {
        var result = new IdentityHashMap<Element, Rect>();
        int fixed = 0, flexible = 0, minimum = 0;
        for (var child : children) {
            if (child.stretchX()) {
                flexible++;
                minimum = Math.max(minimum, ElementLayouts.size(child, text)[0]);
            } else
                fixed += ElementLayouts.size(child, text)[0] + gap;
        }
        int share = flexible == 0 ? width : Math.max(minimum, (width - fixed) / flexible - gap);
        int x = 0, y = 0, rowHeight = 0;
        for (var child : children) {
            int w = Math.min(
                width,
                child.stretchX()
                    ? (horizontal ? share : width)
                    : ElementLayouts.size(child, text)[0]);
            int h = child.measure(text, w)[1];
            if (h < 0)
                throw new IllegalArgumentException("Negative element height");
            if (horizontal && x + w > width && x > 0) {
                y += rowHeight + gap;
                x = 0;
                rowHeight = 0;
            }
            result.put(child, new Rect(horizontal ? x : 0, y, w, h));
            if (horizontal) {
                x += w + gap;
                rowHeight = Math.max(rowHeight, h);
            } else
                y += h + gap;
        }
        return new FlowLayout(
            Collections.unmodifiableMap(result), horizontal ? y + rowHeight : Math.max(0, y - gap));
    }
}
