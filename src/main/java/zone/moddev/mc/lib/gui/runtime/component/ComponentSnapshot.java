package zone.moddev.mc.lib.gui.runtime.component;

/**
 * A component can write only fields declared by its registration; transport owns their addresses.
 */
public interface ComponentSnapshot {
  <T> void put(ComponentSync<T> field, T value);
}
