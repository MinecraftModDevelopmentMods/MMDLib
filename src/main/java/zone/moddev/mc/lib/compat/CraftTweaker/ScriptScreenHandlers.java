package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;

import org.openzen.zencode.java.ZenCodeType;

import org.slf4j.Logger;

import zone.moddev.mc.lib.api.MaterialEventTypes;

/**
 * Registers script functions as screen handlers, under IDs that screen XML
 * names in {@code on-click} (and its variants) and {@code on-hover}.
 *
 * <p>
 * The screen, machine and its XML stay data; the script only supplies what a
 * handler ID does. IDs are {@code mmdlib:<name>}. Every screen's handler IDs
 * are checked at startup, after scripts run, so an XML file naming a handler no
 * script registered fails loading with its file and line. A function that
 * throws is logged, and the game carries on.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/screens/ScreenHandlers")
@ZenCodeType.Name("mods.mmdlib.ScreenHandlers")
public final class ScriptScreenHandlers {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ScriptScreenHandlers() {
    }

    /**
     * Registers {@code handler} as the click handler {@code mmdlib:<name>}; it
     * runs on the server. It is an error if the ID is already a handler.
     *
     * @param name the name; the ID is {@code mmdlib:<name>}
     * @param handler runs on each click
     * @return the handler's ID, to write in screen XML
     * @docParam name "eject"
     * @docParam handler (click) => { click.player.addTag("ejected"); }
     */
    @ZenCodeType.Method
    public static String onClick(String name,
        ScriptScreenClickHandler handler) {
        var id = ScriptDefinitionProperties.mmdlibId(name, "screen handler");
        ScriptDefinitions.catalog().events().registerCallback(id,
            MaterialEventTypes.GUI_CLICK, click -> {
                try {
                    handler.handle(new ScriptScreenClickContext(click));
                } catch (RuntimeException ex) {
                    LOGGER.error("Script screen click handler {} failed", id,
                        ex);
                }
            });
        return id.toString();
    }

    /**
     * Registers {@code handler} as the hover handler {@code mmdlib:<name>}; it
     * runs on the client, for display only. It is an error if the ID is already
     * a handler.
     *
     * @param name the name; the ID is {@code mmdlib:<name>}
     * @param handler runs as the mouse enters and leaves
     * @return the handler's ID, to write in screen XML
     * @docParam name "highlight"
     * @docParam handler (hover) => { println(hover.entered); }
     */
    @ZenCodeType.Method
    public static String onHover(String name,
        ScriptScreenHoverHandler handler) {
        var id = ScriptDefinitionProperties.mmdlibId(name, "screen handler");
        ScriptDefinitions.catalog().events().registerCallback(id,
            MaterialEventTypes.GUI_HOVER, hover -> {
                try {
                    handler.handle(new ScriptScreenHoverContext(hover));
                } catch (RuntimeException ex) {
                    LOGGER.error("Script screen hover handler {} failed", id,
                        ex);
                }
            });
        return id.toString();
    }

    /**
     * Whether a screen handler is registered, from a script or code.
     *
     * @param id the handler ID
     * @return true if registered
     * @docParam id "mmdlib:eject"
     */
    @ZenCodeType.Method
    public static boolean exists(String id) {
        return ScriptDefinitions.catalog().events().callbacks()
            .containsKey(ResourceLocation.parse(id));
    }
}
