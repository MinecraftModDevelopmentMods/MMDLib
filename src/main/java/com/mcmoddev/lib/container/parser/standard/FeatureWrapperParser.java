package com.mcmoddev.lib.container.parser.standard;

import javax.annotation.Nullable;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mcmoddev.lib.MMDLib;
import com.mcmoddev.lib.container.IWidgetContainer;
import com.mcmoddev.lib.container.gui.FeatureWrapperGui;
import com.mcmoddev.lib.container.gui.GuiContext;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.parser.GuiParsingContext;
import com.mcmoddev.lib.container.parser.IWidgetGuiParser;
import com.mcmoddev.lib.feature.IFeatureHolder;
import com.mcmoddev.lib.util.FeatureUtils;
import net.minecraft.util.JsonUtils;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class FeatureWrapperParser implements IWidgetGuiParser {
    private static final String ATTR_FEATURE_KEY = "key";
    private String featureKey;
    private JsonElement originalJson;

    @Override
    public void initialize(GuiParsingContext context, JsonElement json) throws JsonParseException {
        this.originalJson = json;

        if (json.isJsonPrimitive()) {
            this.featureKey = json.getAsJsonPrimitive().getAsString();
        } else if (json.isJsonObject()) {
            this.featureKey = JsonUtils.getString(json.getAsJsonObject(), ATTR_FEATURE_KEY);
        } else {
            this.featureKey = "";
        }

        if (this.featureKey.isEmpty()) {
            throw new JsonParseException("Cannot have a null or empty '" + ATTR_FEATURE_KEY + "' value.");
        }
    }

    @Override
    @Nullable
    @SideOnly(Side.CLIENT)
    public IWidgetGui createRootWidgetGui(GuiContext context) {
        IWidgetContainer container = context.getWidgetProvider();
        IFeatureHolder holder = FeatureUtils.getFeatureHolder(container);
        if (holder != null) {
            return new FeatureWrapperGui(context, holder, this.featureKey);
        }

        MMDLib.logger.error("Could not find feature holder for feature: '" + this.featureKey + "'");
        return null;
    }

    @Nullable
    @Override
    public JsonElement getOriginalJson() {
        return this.originalJson;
    }
}
