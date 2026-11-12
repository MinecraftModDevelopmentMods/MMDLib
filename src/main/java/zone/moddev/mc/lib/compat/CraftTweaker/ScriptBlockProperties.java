package zone.moddev.mc.lib.compat.CraftTweaker;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.api.MaterialEventTypes;
import zone.moddev.mc.lib.common.codecs.BlockTemplate;
import zone.moddev.mc.lib.common.runtime.RedstoneBlock;
import zone.moddev.mc.lib.common.types.FactoryData;
import zone.moddev.mc.lib.common.types.MMDBlock;
import zone.moddev.mc.lib.common.types.MaterialPart;

/**
 * What a script block and a script block module share: the block's properties,
 * whether it has an item, redstone behavior and script handlers, besides the
 * tags, events and factory data of every definition. Values start from the
 * module the builder is based on.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.BlockProperties")
public abstract class ScriptBlockProperties extends ScriptDefinitionProperties {
    private static final ResourceLocation BLOCK_FACTORY = ResourceLocation
        .parse("mmdlib:block");
    private final BlockTemplate base;
    private float hardness;
    private float blastResistance;
    private float friction;
    private float speedFactor;
    private float jumpFactor;
    private int lightLevel;
    private boolean requiresCorrectTool;
    private boolean collision;
    private boolean occlusion;
    private boolean randomTicks;
    private boolean replaceable;
    private String sound;
    private String mapColor;
    private boolean hasItem;
    // Redstone settings, written to factory_data.redstone when any is set.
    private boolean redstone;
    private boolean redstoneInput;
    private boolean redstoneOutput;
    private boolean strongPower;
    private boolean directional;
    private int redstoneDelay = 1;
    private String comparator = "";
    // A constant 0-15 output copied from the base module; scripts only set
    // "state" output.
    private Integer constantOutput;
    private Set<String> outputFaces;
    private Set<String> inputFaces;
    private boolean classHooks;

    ScriptBlockProperties(BlockTemplate base, FactoryData factoryData) {
        super(base.tags(), base.events(), factoryData);
        this.base = base;
        var json = factoryData.json();
        if (json.isJsonObject()
            && json.getAsJsonObject().get("redstone") instanceof JsonObject r) {
            redstone = true;
            redstoneInput = r.has("input") && r.get("input").getAsBoolean();
            redstoneOutput = r.has("output")
                && r.get("output").getAsString().equals("state");
            if (r.has("output") && !redstoneOutput)
                constantOutput = r.get("output").getAsInt();
            strongPower = r.has("strong") && r.get("strong").getAsBoolean();
            directional = r.has("facing") && r.get("facing").getAsBoolean();
            redstoneDelay = r.has("delay") ? r.get("delay").getAsInt() : 0;
            comparator = r.has("comparator")
                ? r.get("comparator").getAsString()
                : "";
            outputFaces = faces(r, "faces");
            inputFaces = faces(r, "inputs");
        }
        var p = base.properties();
        hardness = p.hardness();
        blastResistance = p.blastResistance();
        friction = p.friction();
        speedFactor = p.speedFactor();
        jumpFactor = p.jumpFactor();
        lightLevel = p.lightLevel();
        requiresCorrectTool = p.requiresCorrectTool();
        collision = p.collision();
        occlusion = p.occlusion();
        randomTicks = p.randomTicks();
        replaceable = p.replaceable();
        sound = p.sound();
        mapColor = p.mapColor();
        hasItem = base.hasItem();
    }

    @ZenCodeType.Method
    public ScriptBlockProperties addTag(ScriptBlockTagReference tag) {
        addTags(tag.getId());
        return this;
    }

    @ZenCodeType.Method
    public ScriptBlockProperties destroyTime(float value) {
        setHardness(value);
        return this;
    }

    @ZenCodeType.Method
    public ScriptBlockProperties explosionResistance(float value) {
        setBlastResistance(value);
        return this;
    }

    @ZenCodeType.Method
    public ScriptBlockProperties strength(float value) {
        return strength(value, value);
    }

    @ZenCodeType.Method
    public ScriptBlockProperties strength(float hardness, float resistance) {
        setHardness(hardness);
        setBlastResistance(resistance);
        return this;
    }

    @ZenCodeType.Method
    public ScriptBlockProperties noCollisions() {
        setCollision(false);
        return this;
    }

    @ZenCodeType.Method
    public ScriptBlockProperties noOcclusion() {
        setOcclusion(false);
        return this;
    }

    @ZenCodeType.Method
    public ScriptBlockProperties requiresToolToDrop() {
        return requiresToolToDrop(true);
    }

    @ZenCodeType.Method
    public ScriptBlockProperties requiresToolToDrop(boolean value) {
        setRequiresCorrectTool(value);
        return this;
    }

    @ZenCodeType.Method
    public ScriptBlockProperties noCorrespondingItem() {
        setHasItem(false);
        return this;
    }

    @ZenCodeType.Method
    public ScriptBlockProperties noDrops() {
        resourceOptions = resourceOptions.drops("none", "");
        return this;
    }

    @ZenCodeType.Method
    public ScriptBlockProperties dropsNormally() {
        resourceOptions = resourceOptions.drops("default", "");
        return this;
    }

    @ZenCodeType.Method
    public ScriptBlockProperties dropsSelf() {
        resourceOptions = resourceOptions.drops("self", "");
        return this;
    }

    @ZenCodeType.Method
    public ScriptBlockProperties dropsFrom(String table) {
        var id = ResourceLocation.parse(table);
        var own = definitionId().withPrefix("blocks/");
        if (id.equals(own))
            throw new IllegalArgumentException(
                "Loot table cannot reference itself: " + id);
        resourceOptions = resourceOptions.drops("table", id.toString());
        return this;
    }

    /** Time to mine; -1 makes the block unbreakable. */
    @ZenCodeType.Getter("hardness")
    public float getHardness() {
        return hardness;
    }

    @ZenCodeType.Setter("hardness")
    public void setHardness(float hardness) {
        this.hardness = hardness;
    }

    /** Resistance to explosions. */
    @ZenCodeType.Getter("blastResistance")
    public float getBlastResistance() {
        return blastResistance;
    }

    @ZenCodeType.Setter("blastResistance")
    public void setBlastResistance(float blastResistance) {
        this.blastResistance = blastResistance;
    }

    /** Slipperiness; ice is 0.98, most blocks 0.6. */
    @ZenCodeType.Getter("friction")
    public float getFriction() {
        return friction;
    }

    @ZenCodeType.Setter("friction")
    public void setFriction(float friction) {
        this.friction = friction;
    }

    /** Walking speed multiplier; soul sand is 0.4. */
    @ZenCodeType.Getter("speedFactor")
    public float getSpeedFactor() {
        return speedFactor;
    }

    @ZenCodeType.Setter("speedFactor")
    public void setSpeedFactor(float speedFactor) {
        this.speedFactor = speedFactor;
    }

    /** Jump height multiplier; honey is 0.5. */
    @ZenCodeType.Getter("jumpFactor")
    public float getJumpFactor() {
        return jumpFactor;
    }

    @ZenCodeType.Setter("jumpFactor")
    public void setJumpFactor(float jumpFactor) {
        this.jumpFactor = jumpFactor;
    }

    /** Light emitted, 0 to 15. */
    @ZenCodeType.Getter("lightLevel")
    public int getLightLevel() {
        return lightLevel;
    }

    @ZenCodeType.Setter("lightLevel")
    public void setLightLevel(int lightLevel) {
        this.lightLevel = lightLevel;
    }

    /** Whether the block only drops when mined with the right tool. */
    @ZenCodeType.Getter("requiresCorrectTool")
    public boolean getRequiresCorrectTool() {
        return requiresCorrectTool;
    }

    @ZenCodeType.Setter("requiresCorrectTool")
    public void setRequiresCorrectTool(boolean requiresCorrectTool) {
        this.requiresCorrectTool = requiresCorrectTool;
    }

    /** Whether entities collide with the block. */
    @ZenCodeType.Getter("collision")
    public boolean getCollision() {
        return collision;
    }

    @ZenCodeType.Setter("collision")
    public void setCollision(boolean collision) {
        this.collision = collision;
    }

    /**
     * Whether the block hides the faces of its neighbors; false for glass-like
     * blocks.
     */
    @ZenCodeType.Getter("occlusion")
    public boolean getOcclusion() {
        return occlusion;
    }

    @ZenCodeType.Setter("occlusion")
    public void setOcclusion(boolean occlusion) {
        this.occlusion = occlusion;
    }

    /** Whether the block receives random ticks. */
    @ZenCodeType.Getter("randomTicks")
    public boolean getRandomTicks() {
        return randomTicks;
    }

    @ZenCodeType.Setter("randomTicks")
    public void setRandomTicks(boolean randomTicks) {
        this.randomTicks = randomTicks;
    }

    /** Whether placing a block replaces it, like grass. */
    @ZenCodeType.Getter("replaceable")
    public boolean getReplaceable() {
        return replaceable;
    }

    @ZenCodeType.Setter("replaceable")
    public void setReplaceable(boolean replaceable) {
        this.replaceable = replaceable;
    }

    /** The sound type, such as {@code stone}, {@code wood} or {@code metal}. */
    @ZenCodeType.Getter("sound")
    public String getSound() {
        return sound;
    }

    @ZenCodeType.Setter("sound")
    public void setSound(String sound) {
        this.sound = sound;
    }

    /** The map color, such as {@code stone}, {@code wood} or {@code metal}. */
    @ZenCodeType.Getter("mapColor")
    public String getMapColor() {
        return mapColor;
    }

    @ZenCodeType.Setter("mapColor")
    public void setMapColor(String mapColor) {
        this.mapColor = mapColor;
    }

    /** Whether the block has an item form. */
    @ZenCodeType.Getter("hasItem")
    public boolean getHasItem() {
        return hasItem;
    }

    @ZenCodeType.Setter("hasItem")
    public void setHasItem(boolean hasItem) {
        this.hasItem = hasItem;
    }

    /** The Java implementation that builds the block. */
    abstract ResourceLocation factoryId();

    /**
     * Whether the block reads the redstone power reaching it and runs
     * {@code onRedstoneChanged}. Needs the {@code mmdlib:block} module or
     * factory, as do the other redstone settings.
     */
    @ZenCodeType.Getter("redstoneInput")
    public boolean getRedstoneInput() {
        return redstoneInput;
    }

    @ZenCodeType.Setter("redstoneInput")
    public void setRedstoneInput(boolean redstoneInput) {
        redstone = true;
        this.redstoneInput = redstoneInput;
    }

    /**
     * Whether the block emits a power that handlers set with
     * {@code context.output}.
     */
    @ZenCodeType.Getter("redstoneOutput")
    public boolean getRedstoneOutput() {
        return redstoneOutput;
    }

    @ZenCodeType.Setter("redstoneOutput")
    public void setRedstoneOutput(boolean redstoneOutput) {
        redstone = true;
        this.redstoneOutput = redstoneOutput;
    }

    /**
     * Whether the output also powers through the touching block, like a
     * lever's.
     */
    @ZenCodeType.Getter("strongPower")
    public boolean getStrongPower() {
        return strongPower;
    }

    @ZenCodeType.Setter("strongPower")
    public void setStrongPower(boolean strongPower) {
        redstone = true;
        this.strongPower = strongPower;
    }

    /**
     * Whether the block faces the way its placer looked, so faces can be named
     * {@code front}, {@code
     * back}, {@code left} and {@code right}.
     */
    @ZenCodeType.Getter("directional")
    public boolean getDirectional() {
        return directional;
    }

    @ZenCodeType.Setter("directional")
    public void setDirectional(boolean directional) {
        redstone = true;
        this.directional = directional;
    }

    /**
     * Ticks between a change in power and {@code onRedstoneChanged}; 1 by
     * default, like a fast repeater. 0 reacts at once, but blocks wired into a
     * loop then recurse.
     */
    @ZenCodeType.Getter("redstoneDelay")
    public int getRedstoneDelay() {
        return redstoneDelay;
    }

    @ZenCodeType.Setter("redstoneDelay")
    public void setRedstoneDelay(int redstoneDelay) {
        redstone = true;
        this.redstoneDelay = redstoneDelay;
    }

    /**
     * What a comparator reads: {@code "input"}, {@code "output"}, or {@code ""}
     * for nothing.
     */
    @ZenCodeType.Getter("comparator")
    public String getComparator() {
        return comparator;
    }

    @ZenCodeType.Setter("comparator")
    public void setComparator(String comparator) {
        redstone = true;
        this.comparator = comparator;
    }

    /** The sides the output leaves from; every side unless set. */
    @ZenCodeType.Getter("outputFaces")
    public String[] getOutputFaces() {
        return outputFaces == null
            ? new String[]{ALL}
            : outputFaces.toArray(String[]::new);
    }

    /**
     * Adds sides the output leaves from; the first call replaces "every side".
     */
    @ZenCodeType.Method
    public ScriptBlockProperties addOutputFaces(String... faces) {
        outputFaces = add(outputFaces, faces);
        return this;
    }

    /**
     * Removes output sides; {@code "all"} removes every side, leaving the block
     * silent.
     */
    @ZenCodeType.Method
    public ScriptBlockProperties removeOutputFaces(String... faces) {
        outputFaces = remove(outputFaces, faces);
        return this;
    }

    /** The sides the block reads; every side unless set. */
    @ZenCodeType.Getter("inputFaces")
    public String[] getInputFaces() {
        return inputFaces == null
            ? new String[]{ALL}
            : inputFaces.toArray(String[]::new);
    }

    /**
     * Adds sides the block reads; the first call replaces "every side". Reading
     * only some sides runs {@code onRedstoneChanged} when any of them changes.
     */
    @ZenCodeType.Method
    public ScriptBlockProperties addInputFaces(String... faces) {
        inputFaces = add(inputFaces, faces);
        return this;
    }

    /** Removes read sides; {@code "all"} removes every side. */
    @ZenCodeType.Method
    public ScriptBlockProperties removeInputFaces(String... faces) {
        inputFaces = remove(inputFaces, faces);
        return this;
    }

    /**
     * Runs {@code handler} on the server when the power reaching the block
     * changes, after {@code
     * redstoneDelay} ticks. Turns on {@code redstoneInput}.
     */
    @ZenCodeType.Method
    public ScriptBlockProperties onRedstoneChanged(
        ScriptRedstoneHandler handler) {
        hook(MaterialEventTypes.REDSTONE_CHANGED, handler,
            (h, change) -> h.handle(new ScriptRedstoneContext(change)));
        setRedstoneInput(true);
        return this;
    }

    /**
     * Runs {@code handler} when a player right-clicks the block, on both sides.
     */
    @ZenCodeType.Method
    public ScriptBlockProperties onRightClick(ScriptClickHandler handler) {
        hook(MaterialEventTypes.BLOCK_RIGHT_CLICK, handler, (h, click) -> {
            click.use().consume();
            h.handle(new ScriptClickContext(click));
        });
        return this;
    }

    /**
     * Runs {@code handler} on the server when a player is about to break the
     * block; it can cancel the break.
     */
    @ZenCodeType.Method
    public ScriptBlockProperties onBroken(ScriptBreakHandler handler) {
        hook(MaterialEventTypes.BLOCK_BROKEN, handler,
            (h, broken) -> h.handle(new ScriptBreakContext(broken)));
        return this;
    }

    /** Runs {@code handler} on the server when an entity places the block. */
    @ZenCodeType.Method
    public ScriptBlockProperties onPlaced(ScriptBlockEventHandler handler) {
        hook(MaterialEventTypes.BLOCK_PLACED, handler,
            (h, action) -> h.handle(new ScriptBlockEventContext(action)));
        return this;
    }

    /**
     * Runs {@code handler} when an entity steps on the block, on both sides.
     * Needs the plain block ({@code mmdlib:block}), whose class fires it.
     */
    @ZenCodeType.Method
    public ScriptBlockProperties onSteppedOn(ScriptBlockEventHandler handler) {
        classHooks = true;
        hook(MaterialEventTypes.BLOCK_STEPPED_ON, handler,
            (h, action) -> h.handle(new ScriptBlockEventContext(action)));
        return this;
    }

    /**
     * Runs {@code handler} on the server when the block gets a random tick,
     * like crops growing. Turns on {@code randomTicks}. Needs the plain block
     * ({@code mmdlib:block}).
     */
    @ZenCodeType.Method
    public ScriptBlockProperties onRandomTick(ScriptBlockEventHandler handler) {
        classHooks = true;
        setRandomTicks(true);
        hook(MaterialEventTypes.BLOCK_RANDOM_TICK, handler,
            (h, action) -> h.handle(new ScriptBlockEventContext(action)));
        return this;
    }

    /** Preserve class-only hook validation when snapshotting a module. */
    void copyClassHooksTo(ScriptBlockProperties target) {
        target.classHooks = classHooks;
    }

    /**
     * Checks the redstone settings and class-only hooks now, so the error
     * points at the script.
     *
     * @throws IllegalArgumentException
     *             if they are invalid or the factory is not {@code
     *     mmdlib:block}
     */
    void validateBlock() {
        if (!hasItem && resourceOptions.loot().equals("self"))
            throw new IllegalArgumentException(
                "dropsSelf requires a corresponding item");
        if (classHooks && !factoryId().equals(BLOCK_FACTORY))
            throw new IllegalArgumentException(
                "onSteppedOn and onRandomTick need a block built by "
                    + BLOCK_FACTORY + ", whose class fires them, not "
                    + factoryId() + "; NeoForge has no event for them");
        if (!redstone)
            return;
        if (!factoryId().equals(BLOCK_FACTORY))
            throw new IllegalArgumentException(
                "Redstone settings need a block built by " + BLOCK_FACTORY
                    + ", not " + factoryId());
        RedstoneBlock.Settings.of(new MaterialPart(definitionId(),
            MaterialPart.Kind.BLOCK, Optional.empty(),
            zone.moddev.mc.lib.common.types.PartMetadata.EMPTY, factoryId(),
            factoryData()));
    }

    @Override
    FactoryData factoryData() {
        var data = super.factoryData();
        if (!redstone)
            return data;
        var json = data.json().getAsJsonObject();
        var settings = new JsonObject();
        if (redstoneInput)
            settings.addProperty("input", true);
        if (redstoneOutput)
            settings.addProperty("output", "state");
        else if (constantOutput != null)
            settings.addProperty("output", constantOutput);
        if (strongPower)
            settings.addProperty("strong", true);
        if (directional)
            settings.addProperty("facing", true);
        if (redstoneInput && redstoneDelay > 0)
            settings.addProperty("delay", redstoneDelay);
        // A constant comparator level must stay a number: RedstoneBlock reads
        // strings as property
        // names.
        if (comparator.matches("\\d+"))
            settings.addProperty("comparator", Integer.parseInt(comparator));
        else if (!comparator.isEmpty())
            settings.addProperty("comparator", comparator);
        if (outputFaces != null)
            settings.add("faces", array(outputFaces));
        if (inputFaces != null && redstoneInput)
            settings.add("inputs", array(inputFaces));
        json.add("redstone", settings);
        return new FactoryData(json);
    }

    private static Set<String> add(Set<String> current, String[] faces) {
        var result = current == null
            ? new LinkedHashSet<String>()
            : new LinkedHashSet<>(current);
        for (var face : faces) {
            if (face.equals(ALL))
                throw new IllegalArgumentException(
                    "\"all\" can only remove faces");
            result.add(face);
        }
        return result;
    }

    // "Every side" in the naming the block uses: relative names for a
    // directional block.
    private Set<String> remove(Set<String> current, String[] faces) {
        var result = current == null
            ? new LinkedHashSet<>(directional
                ? List.of("up", "down", "front", "back", "left", "right")
                : List.of("up", "down", "north", "south", "east", "west"))
            : new LinkedHashSet<>(current);
        for (var face : faces)
            if (face.equals(ALL))
                result.clear();
            else
                result.remove(face);
        return result;
    }

    private static Set<String> faces(JsonObject redstone, String key) {
        if (!redstone.has(key))
            return null;
        var faces = new LinkedHashSet<String>();
        redstone.getAsJsonArray(key)
            .forEach(face -> faces.add(face.getAsString()));
        return faces;
    }

    private static JsonArray array(Set<String> faces) {
        var array = new JsonArray();
        faces.forEach(array::add);
        return array;
    }

    /** The template these values describe. */
    BlockTemplate template() {
        return new BlockTemplate(base.name(), base.parent(), base.type(),
            new MMDBlock.Properties(hardness, blastResistance, friction,
                speedFactor, jumpFactor, lightLevel, requiresCorrectTool,
                collision, occlusion, randomTicks, replaceable, sound,
                mapColor),
            tags(), events(), hasItem,
            hasItem ? base.item() : Optional.empty());
    }
}
