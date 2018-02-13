package com.mcmoddev.lib.container.parser;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mcmoddev.lib.MMDLib;
import com.mcmoddev.lib.container.parser.standard.CanvasLayoutParser;
import com.mcmoddev.lib.container.parser.standard.FeatureWrapperParser;
import com.mcmoddev.lib.container.parser.standard.GridLayoutParser;
import com.mcmoddev.lib.container.parser.standard.HorizontalStackLayoutParser;
import com.mcmoddev.lib.container.parser.standard.VerticalStackLayoutParser;

// TODO: make this into a real registry
public final class WidgetParserRegistry {
    private static final Map<String, Supplier<IWidgetGuiParser>> parsers = new HashMap<>();

    public static void initialize() {
        parsers.put("canvas", CanvasLayoutParser::new);
        parsers.put("grid", GridLayoutParser::new);
        parsers.put("vertical", VerticalStackLayoutParser::new);
        parsers.put("horizontal", HorizontalStackLayoutParser::new);

        parsers.put("feature", FeatureWrapperParser::new);
    }

    @Nullable
    public static IWidgetGuiParser parse(GuiParsingContext context, JsonElement json) {
        if (!json.isJsonObject()) {
            MMDLib.logger.error("Cannot parse this: '" + json.toString() + "' into a widget gui parser.");
            return null;
        }

        JsonObject jo = json.getAsJsonObject();
        Set<Map.Entry<String, JsonElement>> entries = jo.entrySet();
        if (entries.size() == 1) {
            Map.Entry<String, JsonElement> entry = entries.iterator().next();
            if (!parsers.containsKey(entry.getKey())) {
                MMDLib.logger.error("Unknown gui widget parser key: '" + entry.getKey() + "'.");
                return null;
            }

            IWidgetGuiParser parser = parsers.get(entry.getKey()).get();
            parser.initialize(context, jo.get(entry.getKey()));
            return parser;
        }
        else {
            // TODO: handle the { type: "", ... } case
        }

        return null;
    }
}
