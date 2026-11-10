package zone.moddev.mc.lib.gui.client;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.gui.model.Text;

/**
 * Translation diagnostics, tooltip numbers, and the screen's emphasis notation.
 */
public final class ScreenText {
    private static final Set<String> MISSING = new HashSet<>();

    private ScreenText() {
    }

    public static Component resolve(Text text, String source) {
        if (!Language.getInstance().has(text.key())
            && MISSING.add(source + ":" + text.key()))
            MMDLib.LOGGER.warn("Missing screen translation {} in {}",
                text.key(), source);
        return component(text);
    }

    public static Component component(Text text) {
        return text.fallback()
            .map(f -> Component.translatableWithFallback(text.key(), f))
            .orElseGet(() -> Component.translatable(text.key()));
    }

    /**
     * Numeric compatibility helper; use the element's displayText hook for UI
     * values.
     */
    public static String number(Object value, boolean full) {
        return zone.moddev.mc.lib.gui.model.DisplayValues.number(value, full);
    }

    /**
     * Numeric compatibility helper; renderers use the element's fillRatio hook.
     */
    public static double ratio(Object value, Object max) {
        return zone.moddev.mc.lib.gui.model.DisplayValues.ratio(value, max);
    }

    public static Component markup(String text) {
        return zone.moddev.mc.lib.gui.model.TextMarkup.markup(text);
    }
}
