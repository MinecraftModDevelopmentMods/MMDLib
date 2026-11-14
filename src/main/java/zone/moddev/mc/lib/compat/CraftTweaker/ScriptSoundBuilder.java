package zone.moddev.mc.lib.compat.CraftTweaker;

import java.util.Optional;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import net.minecraft.resources.ResourceLocation;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.common.types.SoundDefinition;

/**
 * Builds a sound event from a {@code #loader mmdlib} script, or changes one
 * declared in an {@code mmdlib/sound/} file.
 *
 * <p>
 * A new sound event is {@code mmdlib:<name>}; an edited file event keeps its ID
 * and replaces the file's version. What it sounds like comes from
 * {@code sounds.json}, as for any sound event. Call {@link #build()} to queue
 * it for registration.
 *
 * @docParam this new SoundBuilder("machine/hum")
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/sounds/SoundBuilder")
@ZenCodeType.Name("mods.mmdlib.SoundBuilder")
public final class ScriptSoundBuilder {
    private final ResourceLocation id;
    private float range;

    /**
     * Starts a new sound event. It is an error if a file already declares it;
     * use {@link #edit} to change one.
     *
     * @param name the sound event's path, such as {@code "machine/hum"}; its ID
     *            is {@code mmdlib:<name>}
     * @docParam name "machine/hum"
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
     * Starts from a sound event declared in an {@code mmdlib/sound/} file, in
     * any namespace; building it replaces the file's version. It is an error if
     * no file declares it.
     *
     * @param id the sound event's ID
     * @return a builder holding the file's values
     * @docParam id "example:machine/hum"
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

    /**
     * Whether an {@code mmdlib/sound/} file declares a sound event.
     *
     * @param id the sound event's ID
     * @return true if a file declares it
     * @docParam id "example:machine/hum"
     */
    @ZenCodeType.Method
    public static boolean exists(String id) {
        return ScriptDefinitions.soundFiles()
            .containsKey(ResourceLocation.parse(id));
    }

    /**
     * The IDs of every sound event declared in {@code mmdlib/sound/} files.
     *
     * @return the IDs, sorted
     */
    @ZenCodeType.Method
    public static String[] declared() {
        return ScriptDefinitions.soundFiles().keySet().stream()
            .map(ResourceLocation::toString).sorted().toArray(String[]::new);
    }

    /**
     * The sound event's ID, such as {@code mmdlib:machine/hum}.
     *
     * @return the ID
     */
    @ZenCodeType.Getter("name")
    public String getName() {
        return id.toString();
    }

    /**
     * The fixed distance, in blocks, it can be heard from; 0 for Minecraft's
     * default, which grows with the volume it is played at.
     *
     * @return the range, or 0
     */
    @ZenCodeType.Getter("range")
    public float getRange() {
        return range;
    }

    /**
     * Sets the fixed distance, in blocks, it can be heard from; 0 for
     * Minecraft's default. It must not be negative.
     *
     * @param value the range in blocks, or 0
     * @docParam value 24
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
     * file's version of it. It is an error to build the same sound event twice.
     */
    @ZenCodeType.Method
    public void build() {
        ScriptDefinitions.add(id, new SoundDefinition(
            range > 0 ? Optional.of(range) : Optional.empty()));
    }
}
