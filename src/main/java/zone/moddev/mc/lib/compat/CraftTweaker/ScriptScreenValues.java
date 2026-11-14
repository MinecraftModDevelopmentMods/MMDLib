package zone.moddev.mc.lib.compat.CraftTweaker;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import com.mojang.logging.LogUtils;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.openzen.zencode.java.ZenCodeType;

import org.slf4j.Logger;

import zone.moddev.mc.lib.gui.value.ScreenValueProviders;
import zone.moddev.mc.lib.gui.value.ValueType;
import zone.moddev.mc.lib.gui.value.ValueTypes;

/**
 * Registers script functions as screen value providers, which screen XML reads
 * with {@code value="@mmdlib:<name>"} (or {@code max}).
 *
 * <p>
 * The screen stays XML; the script only supplies a value. Each function gets
 * the screen's block entity and runs on the server every tick the screen is
 * open; the value is sent to the player like any other. Screens are bound after
 * scripts run, so an XML file naming a provider no script registered, or one
 * whose kind the element cannot show, fails loading with its file and line. A
 * function that throws is logged once and shows no value.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/screens/ScreenValues")
@ZenCodeType.Name("mods.mmdlib.ScreenValues")
public final class ScriptScreenValues {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ScriptScreenValues() {
    }

    /**
     * Registers a whole-number provider, for gauges, meters, progress bars and
     * labels.
     *
     * @param name the name; the ID is {@code mmdlib:<name>}
     * @param function reads the value from the screen's block entity
     * @return the provider's ID, to write in screen XML after {@code @}
     * @docParam name "height"
     * @docParam function (machine) => machine.blockPos.y
     */
    @ZenCodeType.Method
    public static String whole(String name, ScriptScreenWholeValue function) {
        return register(name, ValueTypes.WHOLE,
            entity -> java.math.BigInteger.valueOf(function.read(entity)));
    }

    /**
     * Registers a decimal provider, for meters and labels.
     *
     * @param name the name; the ID is {@code mmdlib:<name>}
     * @param function reads the value from the screen's block entity
     * @return the provider's ID, to write in screen XML after {@code @}
     * @docParam name "efficiency"
     * @docParam function (machine) => 0.85
     */
    @ZenCodeType.Method
    public static String decimal(String name,
        ScriptScreenDecimalValue function) {
        return register(name, ValueTypes.DECIMAL, function::read);
    }

    /**
     * Registers a true/false provider, for checkboxes and toggles.
     *
     * @param name the name; the ID is {@code mmdlib:<name>}
     * @param function reads the value from the screen's block entity
     * @return the provider's ID, to write in screen XML after {@code @}
     * @docParam name "placed"
     * @docParam function (machine) => machine.hasLevel
     */
    @ZenCodeType.Method
    public static String flag(String name, ScriptScreenFlagValue function) {
        return register(name, ValueTypes.BOOLEAN, function::read);
    }

    /**
     * Registers a text provider, for labels; the text is shown as written.
     *
     * @param name the name; the ID is {@code mmdlib:<name>}
     * @param function reads the text from the screen's block entity
     * @return the provider's ID, to write in screen XML after {@code @}
     * @docParam name "where"
     * @docParam function (machine) => "Ready"
     */
    @ZenCodeType.Method
    public static String text(String name, ScriptScreenTextValue function) {
        return register(name, ValueTypes.TEXT,
            entity -> Component.literal(function.read(entity)));
    }

    /**
     * Whether a screen value provider is registered, from a script or code.
     *
     * @param id the provider ID
     * @return true if registered
     * @docParam id "mmdlib:height"
     */
    @ZenCodeType.Method
    public static boolean exists(String id) {
        return ScreenValueProviders.GLOBAL.get(ResourceLocation.parse(id))
            .isPresent();
    }

    private static <T> String register(String name, ValueType<T> kind,
        Function<BlockEntity, ? extends T> function) {
        ScriptDefinitions.catalog();
        var id = ScriptDefinitionProperties.mmdlibId(name,
            "screen value provider");
        var failed = new AtomicBoolean();
        ScreenValueProviders.GLOBAL.register(id, kind, entity -> {
            try {
                return function.apply(entity);
            } catch (RuntimeException ex) {
                if (!failed.getAndSet(true))
                    LOGGER.error("Script screen value provider {} failed;"
                        + " showing no value", id, ex);
                return null;
            }
        });
        return id.toString();
    }
}
