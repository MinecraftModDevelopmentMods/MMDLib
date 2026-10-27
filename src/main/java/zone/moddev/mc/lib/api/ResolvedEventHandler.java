package zone.moddev.mc.lib.api;

import java.lang.invoke.MethodHandle;
import java.util.Objects;
import zone.moddev.mc.lib.common.codecs.EventExtension;
import zone.moddev.mc.lib.common.types.MMDLibEventCallback;

/** The actual load-time method handle, its metadata, and its typed dispatch adapter. */
public record ResolvedEventHandler<C>(EventExtension.Handler definition, MethodHandle methodHandle,
        MMDLibEventCallback<C> callback) {
    public ResolvedEventHandler { Objects.requireNonNull(definition); Objects.requireNonNull(methodHandle); Objects.requireNonNull(callback); }
    public MaterialEvent<C> event() { return callback.event(); }
    public void invoke(C context) { callback.invoke(context); }
}
