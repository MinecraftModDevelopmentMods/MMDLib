package zone.moddev.mc.lib.gui.client;

import zone.moddev.mc.lib.gui.WidgetAction;
import zone.moddev.mc.lib.gui.layout.Rect;
import zone.moddev.mc.lib.gui.model.Element;

/** Live, client-only access for one widget instance. Values are server snapshots, never getters. */
public interface WidgetAccess {
  Element.Custom element();

  Rect bounds();

  Object value();

  Object maximum();

  boolean pending();

  /** Sends one registered action; false means hidden, pending, or unavailable. */
  <T> boolean submit(WidgetAction<T> action, T value);
}
