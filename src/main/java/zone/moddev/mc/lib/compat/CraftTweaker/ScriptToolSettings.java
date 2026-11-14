package zone.moddev.mc.lib.compat.CraftTweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;

import org.openzen.zencode.java.ZenCodeType;

/**
 * Changes to an {@code mmdlib:tool} item's factory data, for {@code
 * configureTool} on an item or item module. Only the values set here replace
 * the module's; the rest are kept. Tag values are IDs without {@code #}.
 *
 * @docParam this new ToolSettings()
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/definitions/ToolSettings")
@ZenCodeType.Name("mods.mmdlib.ToolSettings")
public final class ScriptToolSettings {
    private final JsonObject data = new JsonObject();

    /**
     * Starts with no changes.
     */
    @ZenCodeType.Constructor
    public ScriptToolSettings() {
    }

    /**
     * Sets the block tag the tool mines.
     *
     * @param tag the block tag's ID
     * @docParam tag "minecraft:mineable/pickaxe"
     */
    @ZenCodeType.Setter("mineable")
    public void setMineable(String tag) {
        data.addProperty("mineable", ResourceLocation.parse(tag).toString());
    }

    /**
     * Sets the block tag the tool mines quickly.
     *
     * @param tag the block tag's ID
     * @docParam tag "minecraft:mineable/pickaxe"
     */
    @ZenCodeType.Setter("fastOn")
    public void setFastOn(String tag) {
        data.addProperty("fast_on", ResourceLocation.parse(tag).toString());
    }

    /**
     * Sets the tool's attack damage.
     *
     * @param value the damage
     * @docParam value 4
     */
    @ZenCodeType.Setter("attackDamage")
    public void setAttackDamage(float value) {
        finite(value);
        data.addProperty("attack_damage", value);
    }

    /**
     * Sets the tool's attack speed modifier.
     *
     * @param value the modifier, usually negative
     * @docParam value -2.5
     */
    @ZenCodeType.Setter("attackSpeed")
    public void setAttackSpeed(float value) {
        finite(value);
        data.addProperty("attack_speed", value);
    }

    /**
     * Replaces the tool's abilities; none clears them. Names are vanilla tool
     * action sets or NeoForge ability names.
     *
     * @param names the ability names
     * @return these settings
     * @docParam names "pickaxe"
     */
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

    /**
     * Replaces how durability scales with the material: the material's tool
     * durability times {@code factor}, at least {@code minimum}. A named
     * special stat, when the material has it, is used instead.
     *
     * @param factor the scale, at least 0
     * @param minimum the least durability, at least 1
     * @param stat a material special stat's name, or empty for none
     * @return these settings
     * @docParam factor 0.5
     * @docParam minimum 1
     * @docParam stat ""
     */
    @ZenCodeType.Method
    public ScriptToolSettings durability(double factor, int minimum,
        String stat) {
        if (minimum < 1)
            throw new IllegalArgumentException(
                "Durability minimum must be at least 1");
        data.add("durability", scaled(factor, minimum, stat));
        return this;
    }

    /**
     * Replaces how mining speed scales with the material: the material's tool
     * efficiency times {@code factor}, at least {@code minimum}. A named
     * special stat, when the material has it, is used instead.
     *
     * @param factor the scale, at least 0
     * @param minimum the least speed, at least 0
     * @param stat a material special stat's name, or empty for none
     * @return these settings
     * @docParam factor 0.5
     * @docParam minimum 1
     * @docParam stat ""
     */
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

    /**
     * Sets the block tag the tool mines, from a reference.
     *
     * @param tag the block tag
     * @return these settings
     * @docParam tag new BlockTagReference("minecraft:mineable/pickaxe")
     */
    @ZenCodeType.Method
    public ScriptToolSettings mineableTag(ScriptBlockTagReference tag) {
        setMineable(tag.getId());
        return this;
    }

    /**
     * Sets the block tag the tool mines quickly, from a reference.
     *
     * @param tag the block tag
     * @return these settings
     * @docParam tag new BlockTagReference("minecraft:mineable/pickaxe")
     */
    @ZenCodeType.Method
    public ScriptToolSettings fastOnTag(ScriptBlockTagReference tag) {
        setFastOn(tag.getId());
        return this;
    }

    JsonObject json() {
        return data.deepCopy();
    }
}
