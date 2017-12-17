package com.mcmoddev.lib.util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.mcmoddev.lib.feature.IFeature;
import com.mcmoddev.lib.feature.IFeatureHolder;
import com.mcmoddev.lib.feature.IFeatureHolderProxy;

public final class FeatureUtils {
    private FeatureUtils() {}

    @Nullable
    public static IFeatureHolder getFeatureHolder(@Nonnull Object thing) {
        if (thing instanceof IFeatureHolder) {
            return IFeatureHolder.class.cast(thing);
        }

        if (thing instanceof IFeatureHolderProxy) {
            return IFeatureHolderProxy.class.cast(thing).getFeatureHolder();
        }

        if (thing instanceof IFeature) {
            return IFeature.class.cast(thing).getHolder();
        }

        return null;
    }
}
