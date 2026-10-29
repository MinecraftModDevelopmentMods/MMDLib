package zone.moddev.mc.lib.api;

import java.lang.invoke.MethodHandle;

/** Adapts an event context to a declared Java method signature, without runtime reflection. */
@FunctionalInterface
public interface MethodEventInvoker<C> {
  /**
   * Calls {@code method} with the arguments this event's signature declares, taken from {@code
   * context}. Use {@code invokeExact} with the exact parameter types so no reflection or boxing
   * happens per call.
   */
  void invoke(MethodHandle method, C context) throws Throwable;
}
