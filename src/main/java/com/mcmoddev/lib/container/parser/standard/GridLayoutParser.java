package com.mcmoddev.lib.container.parser.standard;

import javax.annotation.Nullable;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.gui.layout.GridLayout;
import net.minecraft.util.JsonUtils;

public class GridLayoutParser extends BaseLayoutParser<GridLayout, GridLayoutParser.ChildInfo> {
    private int minRows = 1;
    private int minColumns = 1;

    public GridLayoutParser() {
        super(ChildInfo.class);
    }

    @Nullable
    @Override
    protected GridLayout createLayout() {
        return new GridLayout(this.minColumns, this.minRows);
    }

    @Override
    protected void parseSelfProperties(JsonElement json) throws JsonParseException {
        super.parseSelfProperties(json);

        if (!json.isJsonObject()) {
            // don't care... defaults are good enough.
            return;
        }

        JsonObject jo = json.getAsJsonObject();
        this.minRows = JsonUtils.getInt(jo, "rows", 1);
        this.minColumns = JsonUtils.getInt(jo, "columns", 1);
    }

    static final class ChildInfo extends BaseLayoutParser.BaseChildInfo<GridLayout> {
        private int column;
        private int row;
        private int columnSpan;
        private int rowSpan;

        @Override
        protected void parseChildInfo(JsonElement json) throws JsonParseException {
            if (!json.isJsonObject()) {
                throw new JsonParseException("Could not parse grid child information from: '" + json.toString() + "'.");
            }

            JsonObject jo = json.getAsJsonObject();
            this.column = JsonUtils.getInt(jo, "column", 0);
            this.row = JsonUtils.getInt(jo, "row", 0);
            this.columnSpan = JsonUtils.getInt(jo, "columnSpan", 1);
            this.rowSpan = JsonUtils.getInt(jo, "rowSpan", 1);
        }

        @Override
        protected void addToLayout(GridLayout layout, IWidgetGui gui) {
            layout.addPiece(gui, this.column, this.row, this.columnSpan, this.rowSpan);
        }
    }
}
