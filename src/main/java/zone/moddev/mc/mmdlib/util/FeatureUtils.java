package zone.moddev.mc.mmdlib.util;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import zone.moddev.mc.mmdlib.feature.IFeature;
import zone.moddev.mc.mmdlib.feature.IFeatureHolder;
import zone.moddev.mc.mmdlib.feature.IFeatureHolderProxy;
import mcp.MethodsReturnNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class FeatureUtils {
    private FeatureUtils() {}

    @Nullable
    public static IFeatureHolder getFeatureHolder(Object thing) {
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
