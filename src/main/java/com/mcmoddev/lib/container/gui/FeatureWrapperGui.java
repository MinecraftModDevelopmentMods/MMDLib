package com.mcmoddev.lib.container.gui;

import javax.annotation.Nullable;
import com.mcmoddev.lib.container.IWidgetContainer;
import com.mcmoddev.lib.container.gui.layout.SinglePieceWrapper;
import com.mcmoddev.lib.feature.IFeature;
import com.mcmoddev.lib.feature.IFeatureHolder;

public class FeatureWrapperGui extends SinglePieceWrapper {
    public FeatureWrapperGui(final GuiContext context, final IFeatureHolder holder, final String featureKey) {
        super(extractFeaturePieceSafe(context, holder, featureKey));
    }

    public FeatureWrapperGui(final GuiContext context, final IFeature feature) {
        super(extractFeaturePieceSafe(context, feature));
    }

    public static IWidgetGui extractFeaturePieceSafe(final GuiContext context, final IFeatureHolder holder, final String featureKey) {
        final IWidgetGui piece = extractFeaturePiece(context, holder, featureKey);
        // TODO: put a real missing texture here
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
        // TODO: put a real missing texture here
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
