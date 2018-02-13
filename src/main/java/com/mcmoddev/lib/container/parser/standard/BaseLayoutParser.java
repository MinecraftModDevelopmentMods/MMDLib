package com.mcmoddev.lib.container.parser.standard;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mcmoddev.lib.MMDLib;
import com.mcmoddev.lib.container.gui.GuiContext;
import com.mcmoddev.lib.container.gui.IWidgetGui;
import com.mcmoddev.lib.container.gui.IWidgetLayout;
import com.mcmoddev.lib.container.parser.GuiParsingContext;
import com.mcmoddev.lib.container.parser.IWidgetGuiParser;
import com.mcmoddev.lib.container.parser.WidgetParserRegistry;
import com.mcmoddev.lib.container.widget.IWidget;
import net.minecraft.util.JsonUtils;

public abstract class BaseLayoutParser<L extends IWidgetLayout, C extends BaseLayoutParser.BaseChildInfo> implements IWidgetGuiParser {
    private static final String ATTR_CHILDREN = "children";

    private final Class<C> childInfoClass;

    private final List<C> children = new ArrayList<>();

    private JsonElement originalJson = null;

    protected BaseLayoutParser(Class<C> childInfoClass) {
        this.childInfoClass = childInfoClass;
    }

    @SuppressWarnings("WeakerAccess")
    protected static abstract class BaseChildInfo<L extends IWidgetLayout> {
        IWidgetGuiParser childParser;

        void initialize(IWidgetGuiParser childParser, JsonElement json) throws JsonParseException {
            this.childParser = childParser;
            this.parseChildInfo(json);
        }

        void addToLayout(GuiContext context, L layout) {
            IWidgetGui gui = this.childParser.createRootWidgetGui(context);
            if (gui != null) {
                this.addToLayout(layout, gui);
            }
        }

        abstract protected void parseChildInfo(JsonElement json) throws JsonParseException;
        abstract protected void addToLayout(L layout, IWidgetGui gui);
    }

    @Override
    public final void initialize(GuiParsingContext context, JsonElement json) throws JsonParseException {
        this.originalJson = json;
        this.parseSelfProperties(json);

        JsonArray array;
        if (json.isJsonArray()) {
            array = json.getAsJsonArray();
        } else {
            array = JsonUtils.getJsonArray(json, ATTR_CHILDREN);
        }

        array.forEach(it -> {
            IWidgetGuiParser parser = WidgetParserRegistry.parse(context, it);
            if (parser != null) {
                C child;
                try {
                    child = this.childInfoClass.newInstance();
                }
                catch (IllegalAccessException | InstantiationException e) {
                    MMDLib.logger.error("Could not create type: " + this.childInfoClass.toString());
                    child = null;
                }
                if (child != null) {
                    JsonElement parserJson = parser.getOriginalJson();
                    child.initialize(parser, (parserJson == null) ? it : parserJson);
                    this.children.add(child);
                }
            }
        });
    }

    protected void parseSelfProperties(JsonElement json) throws JsonParseException {
    }

    @Override
    @Nullable
    public JsonElement getOriginalJson() {
        return this.originalJson;
    }

    @Nullable
    @Override
    public final IWidgetGui createRootWidgetGui(GuiContext context) {
        L layout = this.createLayout();
        if (layout == null) {
            return null;
        }

        for(C child: this.children) {
            //noinspection unchecked
            child.addToLayout(context, layout);
        }

        return layout;
    }

    @Nullable
    protected abstract L createLayout();

    @Nullable
    @Override
    public List<IWidget> createWidgets() {
        List<IWidget> widgets = new ArrayList<>();

        for(C child: this.children) {
            child.childParser.createWidgetsToList(widgets);
        }

        return widgets;
    }
}
