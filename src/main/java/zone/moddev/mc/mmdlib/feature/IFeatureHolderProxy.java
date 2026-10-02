package zone.moddev.mc.mmdlib.feature;

import javax.annotation.Nullable;

/**
 * Can be implemented by tile entities that are not directly feature holders.
 */
// Delegates directly to a feature holder; this interface does not expose a capability.
public interface IFeatureHolderProxy {
    /**
     * Gets the actual feature holder instance.
     * @return The actual feature holder instance. Or null if current state doesn't allow one.
     */
    @Nullable
    IFeatureHolder getFeatureHolder();
}
