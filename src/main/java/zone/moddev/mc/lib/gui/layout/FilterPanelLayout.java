package zone.moddev.mc.lib.gui.layout;

import java.util.*;

import zone.moddev.mc.lib.api.automation.FilterControl;

/**
 * Measures an item-provided fragment at the drawer's available width; all output is clipped by its
 * host.
 */
public record FilterPanelLayout(Map<String, Rect> controls, int height) {
  public static FilterPanelLayout of(FilterControl root, int width) {
    if (width < 18) throw new IllegalArgumentException("Editor width");
    FilterControl.flatten(root);
    var result = new LinkedHashMap<String, Rect>();
    int height = place(root, 0, 0, width, result);
    return new FilterPanelLayout(Collections.unmodifiableMap(result), height);
  }

  private static int place(
      FilterControl control, int x, int y, int width, Map<String, Rect> result) {
    if (control instanceof FilterControl.Box box) {
      int height = 0, offset = 0;
      if (box.horizontal()) {
        int rowY = y;
        int fixed = 0, flexible = 0;
        for (var child : box.children())
          if (child instanceof FilterControl.Toggle) fixed += 16;
          else flexible++;
        int share = flexible == 0 ? 14 : Math.max(18, (width - fixed) / flexible - 2);
        for (var child : box.children()) {
          int w = child instanceof FilterControl.Toggle ? 14 : share;
          if (offset + w > width && offset > 0) {
            rowY += height + 2;
            offset = 0;
            height = 0;
          }
          height = Math.max(height, place(child, x + offset, rowY, Math.min(width, w), result));
          offset += w + 2;
        }
        return rowY - y + height;
      }
      for (var child : box.children()) height += place(child, x, y + height, width, result) + 2;
      return Math.max(0, height - 2);
    }
    int height =
        control instanceof FilterControl.Samples samples
            ? ((samples.count() + Math.max(1, width / 18) - 1) / Math.max(1, width / 18)) * 18
            : control instanceof FilterControl.Custom custom
                ? custom.height()
                : control instanceof FilterControl.Toggle ? 14 : 18;
    result.put(
        control.id(), new Rect(x, y, control instanceof FilterControl.Toggle ? 14 : width, height));
    return height;
  }
}
