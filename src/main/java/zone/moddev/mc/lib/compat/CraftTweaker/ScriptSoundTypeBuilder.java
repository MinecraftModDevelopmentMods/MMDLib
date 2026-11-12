package zone.moddev.mc.lib.compat.CraftTweaker;

import java.util.Optional;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import net.minecraft.resources.ResourceLocation;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.common.runtime.SoundTypes;
import zone.moddev.mc.lib.common.types.SoundTypeDefinition;

/**
 * Builds a block sound set from a {@code #loader mmdlib} script, or changes one
 * declared in an {@code mmdlib/sound_type/} file.
 *
 * <p>
 * A block uses it by naming its ID as its {@code sound}, or a material as its
 * {@code blockSound}. Each sound is a sound event ID; anything left empty (or
 * 0) comes from the vanilla {@code base} set.
 *
 * @docParam this new SoundTypeBuilder("steel_plate")
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/sounds/SoundTypeBuilder")
@ZenCodeType.Name("mods.mmdlib.SoundTypeBuilder")
public final class ScriptSoundTypeBuilder {
    private final ResourceLocation id;
    private String base = "stone";
    private ResourceLocation breakSound, step, place, hit, fall;
    private float volume, pitch;

    /**
     * Starts a new sound set named {@code name}, a path such as {@code
     * "steel_plate"}. It is an error if a file already declares it; use
     * {@link #edit} to change it.
     *
     * @param name the sound set's path; its ID is {@code mmdlib:<name>}
     * @docParam name "steel_plate"
     */
    @ZenCodeType.Constructor
    public ScriptSoundTypeBuilder(String name) {
        id = ScriptDefinitionProperties.mmdlibId(name, "sound set");
        if (ScriptDefinitions.soundTypeFiles().containsKey(id))
            throw new IllegalArgumentException("Sound set " + id
                + " is declared in a file; use SoundTypeBuilder.edit(\"" + id
                + "\") to change it");
    }

    private ScriptSoundTypeBuilder(ResourceLocation id,
        SoundTypeDefinition from) {
        this.id = id;
        base = from.base();
        breakSound = from.breakSound().orElse(null);
        step = from.step().orElse(null);
        place = from.place().orElse(null);
        hit = from.hit().orElse(null);
        fall = from.fall().orElse(null);
        volume = from.volume().orElse(0f);
        pitch = from.pitch().orElse(0f);
    }

    /**
     * Starts from the sound set {@code id} declared in an {@code
     * mmdlib/sound_type/} file, in any namespace; building it replaces the
     * file's version. It is an error if no file declares it.
     *
     * @param id the sound set's ID
     * @return a builder holding the file's values
     * @docParam id "example:steel_plate"
     */
    @ZenCodeType.Method
    public static ScriptSoundTypeBuilder edit(String id) {
        var location = ResourceLocation.parse(id);
        var file = ScriptDefinitions.soundTypeFiles().get(location);
        if (file == null)
            throw new IllegalArgumentException(
                "No mmdlib/sound_type file declares " + id);
        return new ScriptSoundTypeBuilder(location, file);
    }

    /**
     * Whether an {@code mmdlib/sound_type/} file declares a sound set.
     *
     * @param id the sound set's ID
     * @return true if a file declares it
     * @docParam id "example:steel_plate"
     */
    @ZenCodeType.Method
    public static boolean exists(String id) {
        return ScriptDefinitions.soundTypeFiles()
            .containsKey(ResourceLocation.parse(id));
    }

    /**
     * The IDs of every set declared in {@code mmdlib/sound_type/} files.
     *
     * @return the IDs, sorted
     */
    @ZenCodeType.Method
    public static String[] declared() {
        return ScriptDefinitions.soundTypeFiles().keySet().stream()
            .map(ResourceLocation::toString).sorted().toArray(String[]::new);
    }

    /**
     * The sound set's ID, such as {@code mmdlib:steel_plate}.
     *
     * @return the ID
     */
    @ZenCodeType.Getter("name")
    public String getName() {
        return id.toString();
    }

    /**
     * The vanilla sound set unset values come from, such as {@code metal}.
     *
     * @return the base set's name
     */
    @ZenCodeType.Getter("base")
    public String getBase() {
        return base;
    }

    /**
     * Sets the vanilla sound set unset values come from. It is an error if it
     * is not one of vanilla's sound sets.
     *
     * @param value a vanilla sound set's name
     * @docParam value "metal"
     */
    @ZenCodeType.Setter("base")
    public void setBase(String value) {
        if (value.indexOf(':') >= 0 || !SoundTypes.exists(value))
            throw new IllegalArgumentException(
                "base must be one of vanilla's sound sets, such as \"metal\";"
                    + " got \"" + value + "\"");
        base = value;
    }

    /**
     * Played when the block is broken; empty for the base set's.
     *
     * @return the sound event's ID, or an empty string
     */
    @ZenCodeType.Getter("breakSound")
    public String getBreakSound() {
        return text(breakSound);
    }

    /**
     * Sets the sound played when the block is broken; empty for the base set's.
     *
     * @param value the sound event's ID, or an empty string
     * @docParam value "mmdlib:block/steel_break"
     */
    @ZenCodeType.Setter("breakSound")
    public void setBreakSound(String value) {
        breakSound = id(value);
    }

    /**
     * Played when an entity walks on it; empty for the base set's.
     *
     * @return the sound event's ID, or an empty string
     */
    @ZenCodeType.Getter("stepSound")
    public String getStepSound() {
        return text(step);
    }

    /**
     * Sets the sound played when an entity walks on it; empty for the base
     * set's.
     *
     * @param value the sound event's ID, or an empty string
     * @docParam value "mmdlib:block/steel_step"
     */
    @ZenCodeType.Setter("stepSound")
    public void setStepSound(String value) {
        step = id(value);
    }

    /**
     * Played when it is placed; empty for the base set's.
     *
     * @return the sound event's ID, or an empty string
     */
    @ZenCodeType.Getter("placeSound")
    public String getPlaceSound() {
        return text(place);
    }

    /**
     * Sets the sound played when it is placed; empty for the base set's.
     *
     * @param value the sound event's ID, or an empty string
     * @docParam value "mmdlib:block/steel_place"
     */
    @ZenCodeType.Setter("placeSound")
    public void setPlaceSound(String value) {
        place = id(value);
    }

    /**
     * Played while it is being mined; empty for the base set's.
     *
     * @return the sound event's ID, or an empty string
     */
    @ZenCodeType.Getter("hitSound")
    public String getHitSound() {
        return text(hit);
    }

    /**
     * Sets the sound played while it is being mined; empty for the base set's.
     *
     * @param value the sound event's ID, or an empty string
     * @docParam value "mmdlib:block/steel_hit"
     */
    @ZenCodeType.Setter("hitSound")
    public void setHitSound(String value) {
        hit = id(value);
    }

    /**
     * Played when an entity lands on it; empty for the base set's.
     *
     * @return the sound event's ID, or an empty string
     */
    @ZenCodeType.Getter("fallSound")
    public String getFallSound() {
        return text(fall);
    }

    /**
     * Sets the sound played when an entity lands on it; empty for the base
     * set's.
     *
     * @param value the sound event's ID, or an empty string
     * @docParam value "mmdlib:block/steel_fall"
     */
    @ZenCodeType.Setter("fallSound")
    public void setFallSound(String value) {
        fall = id(value);
    }

    /**
     * The volume every sound plays at; 0 for the base set's.
     *
     * @return the volume, or 0
     */
    @ZenCodeType.Getter("volume")
    public float getVolume() {
        return volume;
    }

    /**
     * Sets the volume every sound plays at; 0 for the base set's.
     *
     * @param value the volume, or 0
     * @docParam value 0.8
     */
    @ZenCodeType.Setter("volume")
    public void setVolume(float value) {
        volume = nonNegative("volume", value);
    }

    /**
     * The pitch every sound plays at; 0 for the base set's.
     *
     * @return the pitch, or 0
     */
    @ZenCodeType.Getter("pitch")
    public float getPitch() {
        return pitch;
    }

    /**
     * Sets the pitch every sound plays at; 0 for the base set's.
     *
     * @param value the pitch, or 0
     * @docParam value 1.2
     */
    @ZenCodeType.Setter("pitch")
    public void setPitch(float value) {
        pitch = nonNegative("pitch", value);
    }

    /**
     * Finishes the sound set and queues it, replacing a file's version of it.
     * Its sound events are checked when the first block using it is created. It
     * is an error if a script already built it.
     */
    @ZenCodeType.Method
    public void build() {
        ScriptDefinitions.add(id,
            new SoundTypeDefinition(base, Optional.ofNullable(breakSound),
                Optional.ofNullable(step), Optional.ofNullable(place),
                Optional.ofNullable(hit), Optional.ofNullable(fall),
                volume > 0 ? Optional.of(volume) : Optional.empty(),
                pitch > 0 ? Optional.of(pitch) : Optional.empty()));
    }

    private static ResourceLocation id(String value) {
        return value.isEmpty() ? null : ResourceLocation.parse(value);
    }

    private static String text(ResourceLocation value) {
        return value == null ? "" : value.toString();
    }

    private static float nonNegative(String what, float value) {
        if (value < 0)
            throw new IllegalArgumentException(
                what + " must not be negative, got " + value);
        return value;
    }
}
