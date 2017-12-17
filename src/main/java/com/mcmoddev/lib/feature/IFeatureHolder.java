package com.mcmoddev.lib.feature;

public interface IFeatureHolder {
    <T extends IFeature> T addFeature(T feature);

    IFeature[] getFeatures();
    void featuredChanged(IFeature feature);
}
