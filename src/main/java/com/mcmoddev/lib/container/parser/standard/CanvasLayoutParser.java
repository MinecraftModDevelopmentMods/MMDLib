package com.mcmoddev.lib.container.parser.standard;

import javax.annotation.Nullable;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.gui.layout.CanvasLayout;
import net.minecraft.util.JsonUtils;

public class CanvasLayoutParser extends BaseLayoutParser<CanvasLayout, CanvasLayoutParser.ChildInfo> {
    public CanvasLayoutParser() {
        super(ChildInfo.class);
    }

    @Nullable
    @Override
    protected CanvasLayout createLayout() {
        return new CanvasLayout();
    }

    static final class ChildInfo extends BaseLayoutParser.BaseChildInfo<CanvasLayout> {
        private int x;
        private int y;

        @Override
        protected void parseChildInfo(JsonElement json) throws JsonParseException {
            this.x = JsonUtils.getInt(json, "x");
            this.y = JsonUtils.getInt(json, "y");
        }

        @Override
        protected void addToLayout(CanvasLayout layout, IWidgetGui gui) {
            layout.addPiece(gui, this.x, this.y);
        }
    }
}
