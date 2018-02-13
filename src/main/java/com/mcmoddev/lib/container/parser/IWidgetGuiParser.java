package com.mcmoddev.lib.container.parser;

import javax.annotation.Nullable;
import com.google.gson.JsonElement;

public interface IWidgetGuiParser extends IGuiParser {
    @Nullable
    JsonElement getOriginalJson();
}
