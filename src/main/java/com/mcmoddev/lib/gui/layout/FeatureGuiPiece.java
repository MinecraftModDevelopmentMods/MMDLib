package com.mcmoddev.lib.gui.layout;

import javax.annotation.Nullable;
import com.mcmoddev.lib.feature.IFeature;
import com.mcmoddev.lib.feature.IFeatureHolder;
import com.mcmoddev.lib.gui.GuiContext;
import com.mcmoddev.lib.gui.GuiSprites;
import com.mcmoddev.lib.gui.IGuiPiece;
import com.mcmoddev.lib.gui.IGuiPieceProvider;
import com.mcmoddev.lib.gui.piece.GuiForegroundSpritePiece;

public class FeatureGuiPiece extends SinglePieceWrapper {
    public FeatureGuiPiece(GuiContext context, IFeatureHolder holder, String featureKey) {
        super(extractFeaturePieceSafe(context, holder, featureKey));
    }

    public FeatureGuiPiece(GuiContext context, IFeature feature) {
        super(extractFeaturePieceSafe(context, feature));
    }

    public static IGuiPiece extractFeaturePieceSafe(GuiContext context, IFeatureHolder holder, String featureKey) {
        IGuiPiece piece = extractFeaturePiece(context, holder, featureKey);
        // TODO: put a real missing texture here
        return (piece == null) ? new GuiForegroundSpritePiece(GuiSprites.MC_SLOT_BACKGROUND) : piece;
    }

    @Nullable
    public static IGuiPiece extractFeaturePiece(GuiContext context, IFeatureHolder holder, String featureKey) {
        IFeature feature = holder.getFeature(featureKey);
        if (feature instanceof IGuiPieceProvider) {
            return IGuiPieceProvider.class.cast(feature).getRootPiece(context);
        }
        return null;
    }

    public static IGuiPiece extractFeaturePieceSafe(GuiContext context, IFeature feature) {
        IGuiPiece piece = extractFeaturePiece(context, feature);
        // TODO: put a real missing texture here
        return (piece == null) ? new GuiForegroundSpritePiece(GuiSprites.MC_SLOT_BACKGROUND) : piece;
    }

    @Nullable
    public static IGuiPiece extractFeaturePiece(GuiContext context, IFeature feature) {
        if (feature instanceof IGuiPieceProvider) {
            return IGuiPieceProvider.class.cast(feature).getRootPiece(context);
        }
        return null;
    }
}
