package com.mcmoddev.lib.container.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mcmoddev.lib.MMDLib;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

public final class WidgetParser {
    private WidgetParser() {}

    private static final Map<ResourceLocation, IWidgetGuiParser> cachedParsers = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    @Nullable
    public static IWidgetGuiParser parseRootPiece(GuiParsingContext context, ResourceLocation key) {
        ModContainer mod = Loader.instance().getActiveModList()
            .stream()
            .filter(m -> m.getModId().equals(key.getResourceDomain()))
            .findFirst()
            .orElse(null);
        if (mod == null) {
            MMDLib.logger.error("Mod '" + key.getResourceDomain() + "' is not loaded or active!");
            return null;
        }

        AtomicReference<IWidgetGuiParser> parser = new AtomicReference<>(null);
        if (WidgetParser.cachedParsers.containsKey(key)) {
            parser.set(WidgetParser.cachedParsers.get(key));
        }
        else {
            CraftingHelper.findFiles(mod,
                "assets/" + mod.getModId() + "/mmdcontainer/",
                null,
                (root, file) -> {
                    if (!FilenameUtils.getName(file.toString()).equals(key.getResourcePath() + ".json")) {
                        return true;
                    }

                    BufferedReader reader = null;

                    try {
                        reader = Files.newBufferedReader(file);

                        JsonObject jo = JsonUtils.fromJson(GSON, reader, JsonObject.class);
                        if (jo == null) {
                            throw new JsonParseException("Could not create json object from file: '" + file + "'");
                        }
                        JsonObject gui = JsonUtils.getJsonObject(jo, "gui");

                        IWidgetGuiParser guiParser = WidgetParserRegistry.parse(context, gui);
                        if (guiParser != null) {
                            WidgetParser.cachedParsers.put(key, guiParser);
                            parser.set(guiParser);
                        }
                    } catch (JsonParseException jpe) {
                        MMDLib.logger.error("Parsing error loading built-in mmd container '" + key + "'.", jpe);
                        return false;
                    } catch (IOException ioe) {
                        MMDLib.logger.error("Couldn't read build-in mmd container '" + key + "' from '" + file + "'.", ioe);
                        return false;
                    } finally {
                        IOUtils.closeQuietly(reader);
                    }

                    return true;
                }, true, true
            );
        }

        return parser.get();
    }
}
