package zone.moddev.mc.lib.compat.CraftTweaker;

import java.util.Optional;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import net.minecraft.resources.ResourceLocation;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.common.codecs.FluidTemplate;
import zone.moddev.mc.lib.common.types.MMDFluid;
import zone.moddev.mc.lib.common.types.MaterialRuntimeSettings;
import zone.moddev.mc.lib.common.types.StandaloneFluid;

/**
 * Builds a fluid with its own ID from a {@code #loader mmdlib} script, like a
 * {@code mmdlib/standalone/fluid} file: the fluid, its flowing form, a liquid
 * block and a bucket.
 *
 * <p>
 * Values start from the module's template: {@code mmdlib:fluid} (the default),
 * a plain water-like liquid with water's textures, or another fluid module such
 * as {@code mmdlib:molten}. The fluid is {@code mmdlib:<name>}, its bucket
 * {@code mmdlib:<name>_bucket}; MMDLib generates the bucket model, the liquid
 * block's blockstate and English names. Textures are block-atlas textures, as
 * in fluid module files.
 *
 * @docParam this new FluidBuilder("chocolate")
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/fluids/FluidBuilder")
@ZenCodeType.Name("mods.mmdlib.FluidBuilder")
public final class ScriptFluidBuilder {
    private final ResourceLocation id;
    private final ResourceLocation module;
    private final FluidTemplate base;
    private MMDFluid.TypeProperties type;
    private MMDFluid.FlowProperties flow;
    private MMDFluid.Appearance look;
    private boolean hasBlock;
    private boolean hasBucket;
    private String displayName = "";

    /**
     * Starts a plain fluid named {@code name}, a path such as "chocolate".
     *
     * @param name the fluid's path; its ID is {@code mmdlib:<name>}
     * @docParam name "chocolate"
     */
    @ZenCodeType.Constructor
    public ScriptFluidBuilder(String name) {
        this(name, "mmdlib:fluid");
    }

    /**
     * Starts a fluid named {@code name} from the fluid module {@code module},
     * such as {@code mmdlib:molten}. It is an error if no fluid module {@code
     * module} is registered.
     *
     * @param name the fluid's path; its ID is {@code mmdlib:<name>}
     * @param module the fluid module's ID
     * @docParam name "plasma"
     * @docParam module "mmdlib:molten"
     */
    @ZenCodeType.Constructor
    public ScriptFluidBuilder(String name, String module) {
        id = ScriptDefinitionProperties.mmdlibId(name, "fluid");
        this.module = ResourceLocation.parse(module);
        base = ScriptDefinitions.catalog().fluidModule(this.module).template();
        type = base.typeProperties();
        flow = base.flowProperties();
        look = base.appearance();
        hasBlock = base.hasBlock();
        hasBucket = base.hasBucket();
    }

    /**
     * The fluid's ID, such as {@code mmdlib:chocolate}.
     *
     * @return the ID
     */
    @ZenCodeType.Getter("name")
    public String getName() {
        return id.toString();
    }

    /**
     * Density; negative for a gas-like fluid that rises.
     *
     * @param value the density
     * @docParam value 1400
     */
    @ZenCodeType.Setter("density")
    public void setDensity(int value) {
        var t = type;
        type = new MMDFluid.TypeProperties(value, t.viscosity(),
            t.temperature(), t.lightLevel(), t.motionScale(), t.canSwim(),
            t.canDrown(), t.canExtinguish(), t.supportsBoating(),
            t.canHydrate());
    }

    /**
     * Viscosity: how slowly it flows and entities move in it.
     *
     * @param value the viscosity
     * @docParam value 3000
     */
    @ZenCodeType.Setter("viscosity")
    public void setViscosity(int value) {
        var t = type;
        type = new MMDFluid.TypeProperties(t.density(), value, t.temperature(),
            t.lightLevel(), t.motionScale(), t.canSwim(), t.canDrown(),
            t.canExtinguish(), t.supportsBoating(), t.canHydrate());
    }

    /**
     * Temperature, in kelvin; water is 300, lava 1300.
     *
     * @param value the temperature, in kelvin
     * @docParam value 320
     */
    @ZenCodeType.Setter("temperature")
    public void setTemperature(int value) {
        var t = type;
        type = new MMDFluid.TypeProperties(t.density(), t.viscosity(), value,
            t.lightLevel(), t.motionScale(), t.canSwim(), t.canDrown(),
            t.canExtinguish(), t.supportsBoating(), t.canHydrate());
    }

    /**
     * The light its block gives off, 0 to 15.
     *
     * @param value the light level, 0 to 15
     * @docParam value 15
     */
    @ZenCodeType.Setter("lightLevel")
    public void setLightLevel(int value) {
        var t = type;
        type = new MMDFluid.TypeProperties(t.density(), t.viscosity(),
            t.temperature(), value, t.motionScale(), t.canSwim(), t.canDrown(),
            t.canExtinguish(), t.supportsBoating(), t.canHydrate());
    }

    /**
     * How strongly its current pushes entities; water is 0.014.
     *
     * @param value the scale
     * @docParam value 0.007
     */
    @ZenCodeType.Setter("motionScale")
    public void setMotionScale(double value) {
        var t = type;
        type = new MMDFluid.TypeProperties(t.density(), t.viscosity(),
            t.temperature(), t.lightLevel(), value, t.canSwim(), t.canDrown(),
            t.canExtinguish(), t.supportsBoating(), t.canHydrate());
    }

    /**
     * Whether entities can swim in it.
     *
     * @param value true if they can
     * @docParam value false
     */
    @ZenCodeType.Setter("canSwim")
    public void setCanSwim(boolean value) {
        var t = type;
        type = new MMDFluid.TypeProperties(t.density(), t.viscosity(),
            t.temperature(), t.lightLevel(), t.motionScale(), value,
            t.canDrown(), t.canExtinguish(), t.supportsBoating(),
            t.canHydrate());
    }

    /**
     * Whether entities can drown in it.
     *
     * @param value true if they can
     * @docParam value true
     */
    @ZenCodeType.Setter("canDrown")
    public void setCanDrown(boolean value) {
        var t = type;
        type = new MMDFluid.TypeProperties(t.density(), t.viscosity(),
            t.temperature(), t.lightLevel(), t.motionScale(), t.canSwim(),
            value, t.canExtinguish(), t.supportsBoating(), t.canHydrate());
    }

    /**
     * Whether it puts out burning entities.
     *
     * @param value true if it does
     * @docParam value true
     */
    @ZenCodeType.Setter("canExtinguish")
    public void setCanExtinguish(boolean value) {
        var t = type;
        type = new MMDFluid.TypeProperties(t.density(), t.viscosity(),
            t.temperature(), t.lightLevel(), t.motionScale(), t.canSwim(),
            t.canDrown(), value, t.supportsBoating(), t.canHydrate());
    }

    /**
     * Whether boats float on it.
     *
     * @param value true if they do
     * @docParam value false
     */
    @ZenCodeType.Setter("supportsBoating")
    public void setSupportsBoating(boolean value) {
        var t = type;
        type = new MMDFluid.TypeProperties(t.density(), t.viscosity(),
            t.temperature(), t.lightLevel(), t.motionScale(), t.canSwim(),
            t.canDrown(), t.canExtinguish(), value, t.canHydrate());
    }

    /**
     * Whether it hydrates farmland and similar blocks, as water does.
     *
     * @param value true if it does
     * @docParam value false
     */
    @ZenCodeType.Setter("canHydrate")
    public void setCanHydrate(boolean value) {
        var t = type;
        type = new MMDFluid.TypeProperties(t.density(), t.viscosity(),
            t.temperature(), t.lightLevel(), t.motionScale(), t.canSwim(),
            t.canDrown(), t.canExtinguish(), t.supportsBoating(), value);
    }

    /**
     * How many blocks it looks ahead for a downward slope; 1 to 8.
     *
     * @param value the distance, 1 to 8
     * @docParam value 4
     */
    @ZenCodeType.Setter("slopeFindDistance")
    public void setSlopeFindDistance(int value) {
        var f = flow;
        flow = new MMDFluid.FlowProperties(value, f.levelDecreasePerBlock(),
            f.tickRate(), f.explosionResistance(), f.canConvertToSource());
    }

    /**
     * How much its level drops per block it spreads; 1 to 8.
     *
     * @param value the decrease, 1 to 8
     * @docParam value 2
     */
    @ZenCodeType.Setter("levelDecreasePerBlock")
    public void setLevelDecreasePerBlock(int value) {
        var f = flow;
        flow = new MMDFluid.FlowProperties(f.slopeFindDistance(), value,
            f.tickRate(), f.explosionResistance(), f.canConvertToSource());
    }

    /**
     * Ticks between spreads; water is 5, lava 30.
     *
     * @param value the ticks
     * @docParam value 15
     */
    @ZenCodeType.Setter("tickRate")
    public void setTickRate(int value) {
        var f = flow;
        flow = new MMDFluid.FlowProperties(f.slopeFindDistance(),
            f.levelDecreasePerBlock(), value, f.explosionResistance(),
            f.canConvertToSource());
    }

    /**
     * Its block's explosion resistance.
     *
     * @param value the resistance
     * @docParam value 100
     */
    @ZenCodeType.Setter("explosionResistance")
    public void setExplosionResistance(float value) {
        var f = flow;
        flow = new MMDFluid.FlowProperties(f.slopeFindDistance(),
            f.levelDecreasePerBlock(), f.tickRate(), value,
            f.canConvertToSource());
    }

    /**
     * Whether two sources make a new one between them, as water does.
     *
     * @param value true if they do
     * @docParam value false
     */
    @ZenCodeType.Setter("canConvertToSource")
    public void setCanConvertToSource(boolean value) {
        var f = flow;
        flow = new MMDFluid.FlowProperties(f.slopeFindDistance(),
            f.levelDecreasePerBlock(), f.tickRate(), f.explosionResistance(),
            value);
    }

    /**
     * The still texture, such as {@code "minecraft:block/water_still"}.
     *
     * @param texture the texture's ID
     * @docParam texture "minecraft:block/water_still"
     */
    @ZenCodeType.Setter("still")
    public void setStill(String texture) {
        look = new MMDFluid.Appearance(ResourceLocation.parse(texture),
            look.flowing(), look.overlay(), look.tint());
    }

    /**
     * The flowing texture.
     *
     * @param texture the texture's ID
     * @docParam texture "minecraft:block/water_flow"
     */
    @ZenCodeType.Setter("flowing")
    public void setFlowing(String texture) {
        look = new MMDFluid.Appearance(look.still(),
            ResourceLocation.parse(texture), look.overlay(), look.tint());
    }

    /**
     * The texture seen through glass and beside it; empty for none.
     *
     * @param texture the texture's ID, or an empty string
     * @docParam texture "minecraft:block/water_overlay"
     */
    @ZenCodeType.Setter("overlay")
    public void setOverlay(String texture) {
        look = new MMDFluid.Appearance(look.still(), look.flowing(),
            texture.isEmpty()
                ? Optional.empty()
                : Optional.of(ResourceLocation.parse(texture)),
            look.tint());
    }

    /**
     * An ARGB tint over the textures, such as {@code 0xFF5C3A21}; -1 for none.
     *
     * @param argb the tint, as ARGB
     * @docParam argb 0xFF5C3A21
     */
    @ZenCodeType.Setter("tint")
    public void setTint(int argb) {
        look = new MMDFluid.Appearance(look.still(), look.flowing(),
            look.overlay(), argb);
    }

    /**
     * Whether it has a liquid block, so it can be placed in the world.
     *
     * @param value true for a block
     * @docParam value false
     */
    @ZenCodeType.Setter("hasBlock")
    public void setHasBlock(boolean value) {
        hasBlock = value;
    }

    /**
     * Whether it has a bucket.
     *
     * @param value true for a bucket
     * @docParam value true
     */
    @ZenCodeType.Setter("hasBucket")
    public void setHasBucket(boolean value) {
        hasBucket = value;
    }

    /**
     * Its English name, instead of one made from its ID; the bucket is named
     * "{@code <name>} Bucket".
     *
     * @param value the name
     * @docParam value "Liquid Chocolate"
     */
    @ZenCodeType.Setter("displayName")
    public void setDisplayName(String value) {
        displayName = value;
    }

    /**
     * Finishes the fluid and queues it for registration. It is an error if a
     * value is out of range, or if a script already built it.
     */
    @ZenCodeType.Method
    public void build() {
        var template = new FluidTemplate(Optional.empty(), Optional.empty(),
            type, flow, look, hasBlock,
            hasBlock ? base.block() : Optional.empty(), hasBucket,
            hasBucket ? base.bucket() : Optional.empty(), base.events());
        template.toBuilder(); // Range checks, before anything is queued.
        ScriptDefinitions.add(id,
            new StandaloneFluid(module, Optional.of(template), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(),
                MaterialRuntimeSettings.DEFAULT));
        if (!displayName.isEmpty())
            ScriptDefinitions.resources("fluid", id,
                ScriptResourceOptions.DEFAULT.named(displayName));
    }
}
