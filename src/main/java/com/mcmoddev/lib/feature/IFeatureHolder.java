package com.mcmoddev.lib.feature;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.tileentity.TileEntity;

/**
 * Represents a container of {@link IFeature features}.
 */
public interface IFeatureHolder {
    /**
     * Adds a new feature to the container.
     * @param feature The feature to be added.
     * @param <T> The type of the feature to be added. This is only used so that return value has the right type.
     * @return The newly added feature.
     */
    <T extends IFeature> T addFeature(T feature);

    /**
     * Gets all features stored in this container.
     * @return All features stored in this container.
     */
    IFeature[] getFeatures();

    /**
     * Notifies the feature holder that one of the features changed.
     * @param feature The feature that changed.
     * @param level The level of change.
     * @implNote This is usually called by the container features themselves. Do not call directly.
     *           For tile entities that hold features this should be a hint that a call to {@link TileEntity#markDirty()}
     *           should be made. And for item stack holders that the stack's NBT should be updated.
     */
    void featureChanged(IFeature feature, FeatureDirtyLevel level);

    /**
     * Gets a feature from this container.
     * @param key The key of the desired feature.
     * @return The feature corresponding to that key. Or null if such a feature was not found.
     */
    @Nullable
    default IFeature getFeature(final String key) {
        for(final IFeature feature : this.getFeatures()) {
            if (feature.getKey().equals(key)) {
                return feature;
            }
        }
        return null;
    }

    /**
     * Gets a feature from this container that is of a certain type has a certain key.
     * @param expectedType The type of the desired feature.
     * @param key The key of the desired feature.
     * @param <T> The type of the desired feature.
     * @return The feature corresponding to the search criteria. Or null if such a feature was not found.
     */
    @Nullable
    default <T extends IFeature> T getTypedFeature(final Class<T> expectedType, final String key) {
        for(final IFeature feature : this.getFeatures()) {
            if (feature.getKey().equals(key) && expectedType.isAssignableFrom(feature.getClass())) {
                return expectedType.cast(feature);
            }
        }
        return null;
    }

    /**
     * Finds the first feature of the desired type.
     * @param expectedType The type of the feature one is looking for.
     * @param <T> The type of the feature one is looking for.
     * @return First contained feature of that type. Or null if none was found.
     */
    @Nullable
    default <T extends IFeature> T findTypedFeature(final Class<T> expectedType) {
        for(final IFeature feature : this.getFeatures()) {
            if (expectedType.isAssignableFrom(feature.getClass())) {
                return expectedType.cast(feature);
            }
        }
        return null;
    }

    /**
     * Finds all features of a certain type.
     * @param expectedType Type of the desired feature.
     * @param <T> Type of the desired feature.
     * @return List of all features assignable to the specified type.
     */
    default <T extends IFeature> List<T> findTypedFeatures(final Class<T> expectedType) {
        final List<T> list = new ArrayList<>();
        for(final IFeature feature : this.getFeatures()) {
            if (expectedType.isAssignableFrom(feature.getClass())) {
                list.add(expectedType.cast(feature));
            }
        }
        return list;
    }
}
