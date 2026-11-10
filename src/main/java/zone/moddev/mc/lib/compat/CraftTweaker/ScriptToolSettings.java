package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;

import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;

import org.openzen.zencode.java.ZenCodeType;

/**
 * Typed edits to the existing mmdlib:tool factory data; omitted keys retain
 * module values.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.ToolSettings")
public final class ScriptToolSettings {
    private final JsonObject data = new JsonObject();

    @ZenCodeType.Constructor
    public ScriptToolSettings() {
    }

    @ZenCodeType.Setter("mineable")
    public void setMineable(String tag) {
        data.addProperty("mineable", ResourceLocation.parse(tag).toString());
    }

    @ZenCodeType.Setter("fastOn")
    public void setFastOn(String tag) {
        data.addProperty("fast_on", ResourceLocation.parse(tag).toString());
    }

    @ZenCodeType.Setter("attackDamage")
    public void setAttackDamage(float value) {
        finite(value);
        data.addProperty("attack_damage", value);
    }

    @ZenCodeType.Setter("attackSpeed")
    public void setAttackSpeed(float value) {
        finite(value);
        data.addProperty("attack_speed", value);
    }

    /** Replaces the ability list, including an empty list to clear it. */
    @ZenCodeType.Method
    public ScriptToolSettings abilities(String... names) {
        var array = new com.google.gson.JsonArray();
        for (String name : names) {
            if (name.isBlank())
                throw new IllegalArgumentException(
                    "Ability name must not be blank");
            array.add(name);
        }
        data.add("abilities", array);
        return this;
    }

    @ZenCodeType.Method
    public ScriptToolSettings durability(double factor, int minimum,
        String stat) {
        if (minimum < 1)
            throw new IllegalArgumentException(
                "Durability minimum must be at least 1");
        data.add("durability", scaled(factor, minimum, stat));
        return this;
    }

    @ZenCodeType.Method
    public ScriptToolSettings speed(double factor, double minimum,
        String stat) {
        data.add("speed", scaled(factor, minimum, stat));
        return this;
    }

    private static JsonObject scaled(double factor, double minimum,
        String stat) {
        finite(factor);
        finite(minimum);
        if (factor < 0 || minimum < 0)
            throw new IllegalArgumentException(
                "Scale and minimum must be nonnegative");
        var result = new JsonObject();
        result.addProperty("tool_factor", factor);
        result.addProperty("minimum", minimum);
        if (!stat.isEmpty()) {
            if (stat.isBlank())
                throw new IllegalArgumentException(
                    "Stat must be a name or empty");
            result.addProperty("stat", stat);
        }
        return result;
    }

    private static void finite(double value) {
        if (!Double.isFinite(value) || Math.abs(value) > Float.MAX_VALUE)
            throw new IllegalArgumentException(
                "Tool value must be finite and fit a float");
    }

    @ZenCodeType.Method
    public ScriptToolSettings mineableTag(ScriptBlockTagReference tag) {
        setMineable(tag.getId());
        return this;
    }

    @ZenCodeType.Method
    public ScriptToolSettings fastOnTag(ScriptBlockTagReference tag) {
        setFastOn(tag.getId());
        return this;
    }

    JsonObject json() {
        return data.deepCopy();
    }
}
