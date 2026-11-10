package zone.moddev.mc.lib.gui.client;

import java.util.Optional;

/**
 * Optional behavior contracts, including presentation and ingredient drops
 * through adapters.
 */
public interface BehaviorCapabilities {
    <T> Optional<T> capability(Class<T> type);

    static <T> Optional<T> find(ElementBehavior behavior, Class<T> type) {
        if (type.isInstance(behavior))
            return Optional.of(type.cast(behavior));
        return behavior instanceof BehaviorCapabilities provider
            ? provider.capability(type)
            : Optional.empty();
    }
}
