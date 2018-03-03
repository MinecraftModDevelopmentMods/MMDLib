package com.mcmoddev.lib.feature;

import javax.annotation.Nullable;

/**
 * Can be implemented by tile entities that are not directly feature holders.
 */
// TODO: implement this as a capability too
public interface IFeatureHolderProxy {
    /**
     * Gets the actual feature holder instance.
     * @return The actual feature holder instance. Or null if current state doesn't allow one.
     */
    @Nullable
    IFeatureHolder getFeatureHolder();
}
