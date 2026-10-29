package zone.moddev.mc.lib.common.types;

import java.util.Objects;
import java.util.function.Consumer;

import zone.moddev.mc.lib.api.MaterialEvent;

/**
 * A code-registered or load-time method-backed callback with an explicit event/context contract.
 */
public final class MMDLibEventCallback<C> {
  private final MaterialEvent<C> event;
  private final Consumer<? super C> callback;

  /** A callback for {@code event}. */
  public MMDLibEventCallback(MaterialEvent<C> event, Consumer<? super C> callback) {
    this.event = Objects.requireNonNull(event);
    this.callback = Objects.requireNonNull(callback);
  }

  /** The event this callback handles. */
  public MaterialEvent<C> event() {
    return event;
  }

  /**
   * Runs the callback.
   *
   * @throws ClassCastException if {@code context} is not the event's context type
   */
  public void invoke(C context) {
    callback.accept(event.contextType().cast(Objects.requireNonNull(context)));
  }

  /**
   * Runs the callback after checking that {@code expected} is the event it handles; used where the
   * event type is only known at run time.
   *
   * @throws IllegalArgumentException if the callback belongs to another event
   */
  public void invokeChecked(MaterialEvent<?> expected, Object context) {
    if (!event.equals(expected))
      throw new IllegalArgumentException("Callback belongs to event " + event.name());
    invoke(event.contextType().cast(context));
  }
}
