package com.mcmoddev.lib.container.parser.standard;

import javax.annotation.Nullable;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.gui.layout.HorizontalStackLayout;

public class HorizontalStackLayoutParser extends BaseLayoutParser<HorizontalStackLayout, HorizontalStackLayoutParser.ChildInfo> {
    public HorizontalStackLayoutParser() {
        super(ChildInfo.class);
    }

    @Nullable
    @Override
    protected HorizontalStackLayout createLayout() {
        return new HorizontalStackLayout();
    }

    static final class ChildInfo extends BaseLayoutParser.BaseChildInfo<HorizontalStackLayout> {
        @Override
        protected void parseChildInfo(JsonElement json) throws JsonParseException {
        }

        @Override
        protected void addToLayout(HorizontalStackLayout layout, IWidgetGui gui) {
            layout.addPiece(gui);
        }
    }
}
