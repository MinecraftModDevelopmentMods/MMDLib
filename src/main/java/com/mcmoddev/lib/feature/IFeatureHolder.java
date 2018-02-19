package com.mcmoddev.lib.feature;

import javax.annotation.Nullable;

public interface IFeatureHolder {
    <T extends IFeature> T addFeature(T feature);

    IFeature[] getFeatures();
    void featureChanged(IFeature feature, FeatureDirtyLevel level);

    @Nullable
    default IFeature getFeature(final String key) {
        for(final IFeature feature : this.getFeatures()) {
            if (feature.getKey().equals(key)) {
                return feature;
            }
        }
        return null;
    }

    @Nullable
    default <T extends IFeature> T getTypedFeature(final Class<T> expectedType, final String key) {
        for(final IFeature feature : this.getFeatures()) {
            if (feature.getKey().equals(key) && expectedType.isAssignableFrom(feature.getClass())) {
                return expectedType.cast(feature);
            }
        }
        return null;
    }
}
