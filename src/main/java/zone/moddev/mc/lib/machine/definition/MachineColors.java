package zone.moddev.mc.lib.machine.definition;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import zone.moddev.mc.lib.api.capabilities.IoMode;

/** The default machine resource palette. Names are presentation choices, not IO permissions. */
public final class MachineColors {
    private static final Map<String, Integer> PALETTE = Collections
        .unmodifiableMap(new TreeMap<>(Map.ofEntries(
            Map.entry("input.primary", 0x568bd8),
            Map.entry("input.secondary", 0xa184ce),
            Map.entry("input.tertiary", 0xcf84aa),
            Map.entry("output.primary", 0x4dc6d8),
            Map.entry("output.secondary", 0x83b967),
            Map.entry("output.tertiary", 0xd9bb62),
            Map.entry("fluid.primary", 0x508ee0),
            Map.entry("fluid.secondary", 0xee963c),
            Map.entry("fluid.tertiary", 0x9c83cf),
            Map.entry("energy", 0xd85b5b),
            Map.entry("storage", 0x8c9eac),
            Map.entry("manual", 0xaaa0b7))));
    private static final String[] VARIANTS = {"primary", "secondary", "tertiary"};

    private MachineColors() {
    }

    /** Immutable, alphabetically ordered names and their RGB values. */
    public static Map<String, Integer> palette() {
        return PALETTE;
    }

    /** Accepts a case-sensitive palette name or exactly six hexadecimal RGB digits after '#'. */
    public static int parse(String value) {
        if (value != null && value.matches("#[0-9a-fA-F]{6}"))
            return Integer.parseInt(value.substring(1), 16);
        var named = value == null ? null : PALETTE.get(value);
        if (named != null)
            return named;
        throw new IllegalArgumentException("Invalid color='" + value
            + "': expected #RRGGBB or a palette name: " + String.join(", ", PALETTE.keySet()));
    }

    /**
     * Default for the zero-based group ordinal within an item IO mode; variants repeat after three.
     */
    public static int item(IoMode mode, int ordinal) {
        return parse(switch (mode) {
            case INPUT -> "input." + variant(ordinal);
            case OUTPUT -> "output." + variant(ordinal);
            case BOTH -> "storage";
            case DISABLED -> "manual";
        });
    }

    /** Default for the zero-based fluid group ordinal, independent of item groups. */
    public static int fluid(int ordinal) {
        return parse("fluid." + variant(ordinal));
    }

    private static String variant(int ordinal) {
        if (ordinal < 0)
            throw new IllegalArgumentException("Negative palette ordinal");
        return VARIANTS[ordinal % VARIANTS.length];
    }
}
