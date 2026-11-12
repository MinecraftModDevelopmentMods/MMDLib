package zone.moddev.mc.lib.gui.layout;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import zone.moddev.mc.lib.api.automation.FilterControl;

/**
 * Named bounds for integrations inspecting an item-provided element fragment.
 */
public record FilterPanelLayout(Map<String, Rect> controls, int height) {
    public static FilterPanelLayout of(FilterControl root, int width) {
        if (width < 18)
            throw new IllegalArgumentException("Editor width");
        var controls = FilterControl.flatten(root);
        var layout = ElementFragmentLayout.of(root, width, TextMeasure.NONE);
        var result = new LinkedHashMap<String, Rect>();
        for (var control : controls)
            if (control.children().isEmpty())
                result.put(control.id(), layout.bounds().get(control));
        return new FilterPanelLayout(Collections.unmodifiableMap(result),
            layout.height());
    }
}
