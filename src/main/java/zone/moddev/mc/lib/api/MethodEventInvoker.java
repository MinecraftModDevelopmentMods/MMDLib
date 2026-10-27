package zone.moddev.mc.lib.api;

import java.lang.invoke.MethodHandle;

/** Adapts an event context to a declared Java method signature, without runtime reflection. */
@FunctionalInterface
public interface MethodEventInvoker<C> {
    void invoke(MethodHandle method, C context) throws Throwable;
}
