package zone.moddev.mc.lib.compat.CraftTweaker;

import java.util.Map;

import net.minecraft.resources.ResourceLocation;

/** Immutable script-pack metadata, kept outside core definitions. */
record ScriptResourceOptions(String displayName, String loot, String table) {
    static final ScriptResourceOptions DEFAULT = new ScriptResourceOptions("",
        "default", "");

    ScriptResourceOptions named(String name) {
        if (name.isBlank())
            throw new IllegalArgumentException(
                "Display name must not be blank");
        return new ScriptResourceOptions(name, loot, table);
    }

    ScriptResourceOptions drops(String mode, String reference) {
        return new ScriptResourceOptions(displayName, mode, reference);
    }

    static String key(String kind, ResourceLocation id) {
        return kind + "|" + id;
    }

    static ScriptResourceOptions find(
        Map<String, ScriptResourceOptions> options, String kind,
        ResourceLocation id, String moduleKind, ResourceLocation module) {
        return options.getOrDefault(key(kind, id),
            options.getOrDefault(key(moduleKind, module), DEFAULT));
    }

    String label(String fallback, String material) {
        return displayName.isEmpty()
            ? fallback
            : displayName.replace("__material__", material);
    }
}
