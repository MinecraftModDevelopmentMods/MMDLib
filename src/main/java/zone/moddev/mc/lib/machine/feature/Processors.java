package zone.moddev.mc.lib.machine.feature;

import java.util.Optional;

import zone.moddev.mc.lib.gui.runtime.ScreenHost;

/**
 * Typed lookup through shared host contracts, independent of a concrete block
 * entity.
 */
public final class Processors {
    private Processors() {
    }

    public static <T> Optional<T> find(Object host, String key, Class<T> type) {
        var features = host instanceof FeatureHost direct
            ? direct
            : host instanceof ScreenHost screen
                ? screen.screenFeatures()
                : null;
        return features == null
            ? Optional.empty()
            : features.findProcessor(key, type);
    }
}
