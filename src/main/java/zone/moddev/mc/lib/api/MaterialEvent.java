package zone.moddev.mc.lib.api;

import java.util.Objects;

/** Typed dispatch key. JSON bindings use name(); code supplies the context class. */
public record MaterialEvent<C>(String name, Class<C> contextType) {
    public MaterialEvent {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Blank event name");
        Objects.requireNonNull(contextType);
    }
}
