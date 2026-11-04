package zone.moddev.mc.lib.gui.client;

/** Creates a fresh interactive instance for each element in each open screen. */
@FunctionalInterface
public interface WidgetFactory {
  WidgetInstance create(WidgetAccess access);
}
