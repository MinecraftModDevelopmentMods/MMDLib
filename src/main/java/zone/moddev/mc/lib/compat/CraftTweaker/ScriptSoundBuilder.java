package zone.moddev.mc.lib.compat.CraftTweaker;

import java.util.Optional;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;

import net.minecraft.resources.ResourceLocation;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.common.types.SoundDefinition;

/**
 * Builds a sound event from a {@code #loader mmdlib} script, or changes one
 * declared in an {@code mmdlib/sound/} file:
 *
 * <pre>{@code
 * var hum = new SoundBuilder("machine/hum");
 * hum.range = 24;
 * hum.build();
 * }</pre>
 *
 * <p>
 * A new sound event is {@code mmdlib:<name>}; an edited file event keeps its ID
 * and replaces the file's version. What it sounds like comes from
 * {@code sounds.json}, as for any sound event.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.SoundBuilder")
public final class ScriptSoundBuilder {
    private final ResourceLocation id;
    private float range;

    /**
     * Starts a new sound event named {@code name}, a path such as
     * {@code "machine/hum"}.
     *
     * @throws IllegalArgumentException
     *             if a file already declares it; use {@link #edit} to change it
     */
    @ZenCodeType.Constructor
    public ScriptSoundBuilder(String name) {
        id = ScriptDefinitionProperties.mmdlibId(name, "sound event");
        if (ScriptDefinitions.soundFiles().containsKey(id))
            throw new IllegalArgumentException("Sound event " + id
                + " is declared in a file; use SoundBuilder.edit(\"" + id
                + "\") to change it");
    }

    private ScriptSoundBuilder(ResourceLocation id, SoundDefinition from) {
        this.id = id;
        range = from.range().orElse(0f);
    }

    /**
     * Starts from the sound event {@code id} declared in an
     * {@code mmdlib/sound/} file, in any namespace; building it replaces the
     * file's version.
     *
     * @throws IllegalArgumentException
     *             if no file declares it
     */
    @ZenCodeType.Method
    public static ScriptSoundBuilder edit(String id) {
        var location = ResourceLocation.parse(id);
        var file = ScriptDefinitions.soundFiles().get(location);
        if (file == null)
            throw new IllegalArgumentException(
                "No mmdlib/sound file declares " + id);
        return new ScriptSoundBuilder(location, file);
    }

    /** Whether an {@code mmdlib/sound/} file declares {@code id}. */
    @ZenCodeType.Method
    public static boolean exists(String id) {
        return ScriptDefinitions.soundFiles()
            .containsKey(ResourceLocation.parse(id));
    }

    /** The IDs of every sound event declared in {@code mmdlib/sound/} files. */
    @ZenCodeType.Method
    public static String[] declared() {
        return ScriptDefinitions.soundFiles().keySet().stream()
            .map(ResourceLocation::toString).sorted().toArray(String[]::new);
    }

    /** The sound event's ID, such as {@code mmdlib:machine/hum}. */
    @ZenCodeType.Getter("name")
    public String getName() {
        return id.toString();
    }

    /**
     * The fixed distance, in blocks, it can be heard from; 0 for Minecraft's
     * default, which grows with the volume it is played at.
     */
    @ZenCodeType.Getter("range")
    public float getRange() {
        return range;
    }

    /**
     * @throws IllegalArgumentException
     *             if {@code value} is negative
     */
    @ZenCodeType.Setter("range")
    public void setRange(float value) {
        if (value < 0)
            throw new IllegalArgumentException(
                "range must not be negative, got " + value);
        range = value;
    }

    /**
     * Finishes the sound event and queues it for registration, replacing a
     * file's version of it.
     *
     * @throws IllegalStateException
     *             if a script already built it
     */
    @ZenCodeType.Method
    public void build() {
        ScriptDefinitions.add(id, new SoundDefinition(
            range > 0 ? Optional.of(range) : Optional.empty()));
    }
}
