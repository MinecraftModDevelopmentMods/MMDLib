package zone.moddev.mc.mmdlib.container.gui;

import javax.annotation.Nullable;
import zone.moddev.mc.mmdlib.container.IWidgetContainer;
import zone.moddev.mc.mmdlib.container.gui.layout.SinglePieceWrapper;
import zone.moddev.mc.mmdlib.feature.IFeature;
import zone.moddev.mc.mmdlib.feature.IFeatureHolder;

public class FeatureWrapperGui extends SinglePieceWrapper {
    public FeatureWrapperGui(final GuiContext context, final IFeatureHolder holder, final String featureKey) {
        super(extractFeaturePieceSafe(context, holder, featureKey));
    }

    public FeatureWrapperGui(final GuiContext context, final IFeature feature) {
        super(extractFeaturePieceSafe(context, feature));
    }

    public static IWidgetGui extractFeaturePieceSafe(final GuiContext context, final IFeatureHolder holder, final String featureKey) {
        final IWidgetGui piece = extractFeaturePiece(context, holder, featureKey);
        // Use a slot background when this feature has no GUI.
        return (piece == null) ? new SpriteForegroundGui(GuiSprites.MC_SLOT_BACKGROUND) : piece;
    }

    @Nullable
    public static IWidgetGui extractFeaturePiece(final GuiContext context, final IFeatureHolder holder, final String featureKey) {
        final IFeature feature = holder.getFeature(featureKey);
        if (feature instanceof IWidgetContainer) {
            return IWidgetContainer.class.cast(feature).getRootWidgetGui(context);
        }
        return null;
    }

    public static IWidgetGui extractFeaturePieceSafe(final GuiContext context, final IFeature feature) {
        final IWidgetGui piece = extractFeaturePiece(context, feature);
        // Use a slot background when this feature has no GUI.
        return (piece == null) ? new SpriteForegroundGui(GuiSprites.MC_SLOT_BACKGROUND) : piece;
    }

    @Nullable
    public static IWidgetGui extractFeaturePiece(final GuiContext context, final IFeature feature) {
        if (feature instanceof IWidgetContainer) {
            return IWidgetContainer.class.cast(feature).getRootWidgetGui(context);
        }
        return null;
    }
}
