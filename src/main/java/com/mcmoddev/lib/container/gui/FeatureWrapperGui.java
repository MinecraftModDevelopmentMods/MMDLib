package com.mcmoddev.lib.container.gui;

import javax.annotation.Nullable;
import com.mcmoddev.lib.container.IWidgetContainer;
import com.mcmoddev.lib.container.gui.layout.SinglePieceWrapper;
import com.mcmoddev.lib.feature.IFeature;
import com.mcmoddev.lib.feature.IFeatureHolder;

public class FeatureWrapperGui extends SinglePieceWrapper {
    public FeatureWrapperGui(GuiContext context, IFeatureHolder holder, String featureKey) {
        super(extractFeaturePieceSafe(context, holder, featureKey));
    }

    public FeatureWrapperGui(GuiContext context, IFeature feature) {
        super(extractFeaturePieceSafe(context, feature));
    }

    public static IWidgetGui extractFeaturePieceSafe(GuiContext context, IFeatureHolder holder, String featureKey) {
        IWidgetGui piece = extractFeaturePiece(context, holder, featureKey);
        // TODO: put a real missing texture here
        return (piece == null) ? new SpriteForegroundGui(GuiSprites.MC_SLOT_BACKGROUND) : piece;
    }

    @Nullable
    public static IWidgetGui extractFeaturePiece(GuiContext context, IFeatureHolder holder, String featureKey) {
        IFeature feature = holder.getFeature(featureKey);
        if (feature instanceof IWidgetContainer) {
            return IWidgetContainer.class.cast(feature).getRootWidgetGui(context);
        }
        return null;
    }

    public static IWidgetGui extractFeaturePieceSafe(GuiContext context, IFeature feature) {
        IWidgetGui piece = extractFeaturePiece(context, feature);
        // TODO: put a real missing texture here
        return (piece == null) ? new SpriteForegroundGui(GuiSprites.MC_SLOT_BACKGROUND) : piece;
    }

    @Nullable
    public static IWidgetGui extractFeaturePiece(GuiContext context, IFeature feature) {
        if (feature instanceof IWidgetContainer) {
            return IWidgetContainer.class.cast(feature).getRootWidgetGui(context);
        }
        return null;
    }
}
