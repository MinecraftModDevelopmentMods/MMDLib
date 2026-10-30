package zone.moddev.mc.lib.api;

import java.util.Objects;

/**
 * A typed event key, such as {@code player-tick}. JSON bindings refer to it by {@link #name()};
 * Java code uses the key itself, so the compiler checks that callbacks and dispatches agree on the
 * context type.
 *
 * <p>Keys are created by {@link MaterialEvents#registerEvent}; the built-in ones are in {@link
 * MaterialEventTypes}.
 *
 * @param name the name used in JSON {@code event} maps, e.g. {@code "tool-right-click"}
 * @param contextType the class of the object passed to callbacks when the event fires
 * @param <C> the context type
 */
public record MaterialEvent<C>(String name, Class<C> contextType) {
  /**
   * Requires a nonblank name and a context type.
   *
   * @throws IllegalArgumentException if {@code name} is null or blank
   */
  public MaterialEvent {
    if (name == null || name.isBlank()) throw new IllegalArgumentException("Blank event name");
    Objects.requireNonNull(contextType);
  }
}
