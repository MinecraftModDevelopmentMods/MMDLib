package zone.moddev.mc.lib.gui.runtime.component;

/** Typed reads return decoded copies, never mutable synchronization storage. */
public interface ComponentStateView {
  <T> T get(ComponentSync<T> field);
}
