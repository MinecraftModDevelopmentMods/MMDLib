package zone.moddev.mc.lib.common.types;

import zone.moddev.mc.lib.api.MaterialEvent;
import java.util.Objects;
import java.util.function.Consumer;

/** Code-owned callback with an explicit event/context contract. */
public final class MMDLibEventCallback<C> {
    private final MaterialEvent<C> event;
    private final Consumer<? super C> callback;
    public MMDLibEventCallback(MaterialEvent<C> event, Consumer<? super C> callback) {
        this.event = Objects.requireNonNull(event); this.callback = Objects.requireNonNull(callback);
    }
    public MaterialEvent<C> event() { return event; }
    public void invoke(C context) { callback.accept(event.contextType().cast(Objects.requireNonNull(context))); }
    public void invokeChecked(MaterialEvent<?> expected, Object context) {
        if (!event.equals(expected)) throw new IllegalArgumentException("Callback belongs to event " + event.name());
        invoke(event.contextType().cast(context));
    }
}
