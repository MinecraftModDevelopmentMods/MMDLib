package com.mcmoddev.lib.container.parser.standard;

import javax.annotation.Nullable;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.gui.layout.VerticalStackLayout;

public class VerticalStackLayoutParser extends BaseLayoutParser<VerticalStackLayout, VerticalStackLayoutParser.ChildInfo> {
    public VerticalStackLayoutParser() {
        super(ChildInfo.class);
    }

    @Nullable
    @Override
    protected VerticalStackLayout createLayout() {
        return new VerticalStackLayout();
    }

    static final class ChildInfo extends BaseLayoutParser.BaseChildInfo<VerticalStackLayout> {
        @Override
        protected void parseChildInfo(JsonElement json) throws JsonParseException {
        }

        @Override
        protected void addToLayout(VerticalStackLayout layout, IWidgetGui gui) {
            layout.addPiece(gui);
        }
    }
}
