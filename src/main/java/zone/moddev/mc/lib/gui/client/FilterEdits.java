package zone.moddev.mc.lib.gui.client;

import zone.moddev.mc.lib.api.automation.FilterControl;

/**
 * Behavior service supplied by a filter-editor host; server authorization remains in its transport.
 */
@FunctionalInterface
public interface FilterEdits {
  boolean submit(FilterControl control, int entry, String value);

  static boolean submit(ElementAccess access, FilterControl control, int entry, String value) {
    return !access.pending(control)
        && access
            .service(FilterEdits.class)
            .map(service -> service.submit(control, entry, value))
            .orElse(false);
  }
}
