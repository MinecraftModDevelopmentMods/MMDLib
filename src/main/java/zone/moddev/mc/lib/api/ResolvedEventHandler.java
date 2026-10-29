package zone.moddev.mc.lib.api;

import java.lang.invoke.MethodHandle;
import java.util.Objects;

import zone.moddev.mc.lib.common.codecs.EventExtension;
import zone.moddev.mc.lib.common.types.MMDLibEventCallback;

/**
 * A handler from an extension file, resolved at load: its definition, the static method it names,
 * and the callback that adapts an event context to that method's parameters.
 *
 * @param definition the handler entry as written in the extension file
 * @param methodHandle the resolved {@code public static void} method
 * @param callback the typed callback registered under the handler's ID
 * @param <C> the event's context type
 */
public record ResolvedEventHandler<C>(
    EventExtension.Handler definition, MethodHandle methodHandle, MMDLibEventCallback<C> callback) {
  public ResolvedEventHandler {
    Objects.requireNonNull(definition);
    Objects.requireNonNull(methodHandle);
    Objects.requireNonNull(callback);
  }

  /** The event this handler is registered for. */
  public MaterialEvent<C> event() {
    return callback.event();
  }

  /** Runs the handler with {@code context}. */
  public void invoke(C context) {
    callback.invoke(context);
  }
}
