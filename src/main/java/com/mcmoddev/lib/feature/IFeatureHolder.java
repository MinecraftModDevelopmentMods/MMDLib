package com.mcmoddev.lib.feature;

import javax.annotation.Nullable;

public interface IFeatureHolder {
    <T extends IFeature> T addFeature(T feature);

    IFeature[] getFeatures();
    void featuredChanged(IFeature feature);

    @Nullable
    default IFeature getFeature(String key) {
        for(IFeature feature : this.getFeatures()) {
            if (feature.getKey().equals(key)) {
                return feature;
            }
        }
        return null;
    }

    @Nullable
    default <T extends IFeature> T getTypedFeature(Class<T> expectedType, String key) {
        for(IFeature feature : this.getFeatures()) {
            if (feature.getKey().equals(key) && expectedType.isAssignableFrom(feature.getClass())) {
                return expectedType.cast(feature);
            }
        }
        return null;
    }
}
