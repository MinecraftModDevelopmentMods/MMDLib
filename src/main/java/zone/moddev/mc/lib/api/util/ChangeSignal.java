package zone.moddev.mc.lib.api.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Instance-owned change notifications; subscribe, publish and unsubscribe on
 * the owning thread.
 */
public final class ChangeSignal {
    private final List<Runnable> listeners = new ArrayList<>();

    /**
     * Returns an idempotent unsubscribe action. Does not replace the owner's
     * persistence callback.
     */
    public Runnable subscribe(Runnable listener) {
        Objects.requireNonNull(listener);
        // Wrap so two registrations of the same callback have independent
        // lifetimes.
        Runnable registered = new Runnable() {
            @Override
            public void run() {
                listener.run();
            }
        };
        listeners.add(registered);
        return () -> listeners.remove(registered);
    }

    public void changed() {
        if (!listeners.isEmpty())
            List.copyOf(listeners).forEach(Runnable::run);
    }
}
