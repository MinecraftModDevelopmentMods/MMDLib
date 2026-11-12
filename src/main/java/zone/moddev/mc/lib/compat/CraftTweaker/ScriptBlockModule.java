package zone.moddev.mc.lib.compat.CraftTweaker;

import java.util.List;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import net.minecraft.resources.ResourceLocation;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.common.modules.DataModules;
import zone.moddev.mc.lib.common.types.PartMetadata;

/**
 * Defines a new block module, a kind of block, from a {@code #loader mmdlib}
 * script, like a {@code data/<namespace>/block} file. Once built it can be used
 * like any other module: materials select it as a part (by its name, in script
 * materials and material files) and {@link ScriptBlockBuilder} builds
 * standalone blocks with it.
 *
 * <p>
 * A module starts as a copy of the module it is based on: its Java factory,
 * factory data, template and block entity, but not its aliases. Change {@code
 * factory} to use another Java implementation. A material's {@code
 * copper_pillar} gets generated art from the template of the module it is based
 * on, or else of its factory; a module with neither needs its art supplied.
 *
 * @docParam this new BlockModule("pillar")
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/definitions/BlockModule")
@ZenCodeType.Name("mods.mmdlib.BlockModule")
public final class ScriptBlockModule extends ScriptBlockProperties {
    private final ResourceLocation id;
    private final DataModules.Block base;
    private ResourceLocation factory;
    private boolean materialDefaults;

    /**
     * Starts module {@code name} (a path such as {@code "pillar"}) as a copy of
     * {@code mmdlib:block}.
     *
     * @param name the module's path; its ID is {@code mmdlib:<name>}
     * @docParam name "pillar"
     */
    @ZenCodeType.Constructor
    public ScriptBlockModule(String name) {
        this(name, "mmdlib:block");
    }

    /**
     * Starts module {@code name} as a copy of module {@code basedOn}, such as
     * {@code mmdlib:stairs}.
     *
     * @param name the module's path; its ID is {@code mmdlib:<name>}
     * @param basedOn the block module to copy
     * @docParam name "pillar"
     * @docParam basedOn "mmdlib:stairs"
     */
    @ZenCodeType.Constructor
    public ScriptBlockModule(String name, String basedOn) {
        this(mmdlibId(name, "block module"), ResourceLocation.parse(basedOn));
    }

    private final ResourceLocation baseId;

    /**
     * Starts module {@code name} as a copy of another block module, from a
     * reference.
     *
     * @param name the module's path; its ID is {@code mmdlib:<name>}
     * @param basedOn the block module to copy
     * @docParam name "pillar"
     * @docParam basedOn new BlockModuleReference("mmdlib:stairs")
     */
    @ZenCodeType.Constructor
    public ScriptBlockModule(String name, ScriptBlockModuleReference basedOn) {
        this(name, basedOn.getId());
    }

    /**
     * A reference to this module, for builders that take a module.
     *
     * @return the reference
     */
    @ZenCodeType.Getter("reference")
    public ScriptBlockModuleReference getReference() {
        return new ScriptBlockModuleReference(id.toString());
    }

    /**
     * Copies this module's current configuration to a new module; this one need
     * not be built yet. Later changes to either do not affect the other.
     *
     * @param name the new module's path
     * @return the copy
     * @docParam name "large_pillar"
     */
    @ZenCodeType.Method
    public ScriptBlockModule copy(String name) {
        var targetId = mmdlibId(name, "block module");
        if (targetId.equals(id))
            throw new IllegalArgumentException(
                "Copy needs a different module ID");
        var snapshot = new DataModules.Block(factory, base.requiredMod(),
            materialDefaults, factoryData(), template(), base.part());
        var target = new ScriptBlockModule(targetId, baseId, snapshot);
        copyClassHooksTo(target);
        target.resourceOptions = resourceOptions;
        copyHooksTo(target);
        return target;
    }

    private ScriptBlockModule(ResourceLocation id, ResourceLocation baseId) {
        this(id, baseId, ScriptDefinitions.catalog().blockModule(baseId));
    }

    private ScriptBlockModule(ResourceLocation id, ResourceLocation baseId,
        DataModules.Block base) {
        super(base.template(), base.factoryData());
        this.id = id;
        resourceOptions = ScriptDefinitions.resourceOptions("block_module",
            baseId);
        this.baseId = baseId;
        this.base = base;
        factory = base.factory();
        materialDefaults = base.materialDefaults();
    }

    /**
     * The module's ID, such as {@code mmdlib:pillar}.
     *
     * @return the ID
     */
    @ZenCodeType.Getter("name")
    public String getName() {
        return id.toString();
    }

    /**
     * The Java implementation that builds the blocks, such as {@code
     * mmdlib:stairs}.
     *
     * @return the implementation's ID
     */
    @ZenCodeType.Getter("factory")
    public String getFactory() {
        return factory.toString();
    }

    /**
     * Sets the Java implementation that builds the blocks.
     *
     * @param factory the implementation's ID
     * @docParam factory "mmdlib:stairs"
     */
    @ZenCodeType.Setter("factory")
    public void setFactory(String factory) {
        var id = ResourceLocation.parse(factory);
        ScriptDefinitions.catalog().blockImplementation(id);
        this.factory = id;
    }

    /**
     * Whether a material's stats (hardness, harvest tags...) override the
     * template's.
     *
     * @return true if material stats override the template
     */
    @ZenCodeType.Getter("materialDefaults")
    public boolean getMaterialDefaults() {
        return materialDefaults;
    }

    /**
     * Sets whether a material's stats (hardness, harvest tags...) override the
     * template's.
     *
     * @param materialDefaults true to use the material's stats
     * @docParam materialDefaults false
     */
    @ZenCodeType.Setter("materialDefaults")
    public void setMaterialDefaults(boolean materialDefaults) {
        this.materialDefaults = materialDefaults;
    }

    /**
     * Registers the module. Script lines after this, and every material file,
     * can use it.
     *
     * @throws IllegalArgumentException if a value is invalid or the name is
     *             taken
     */
    @Override
    ResourceLocation definitionId() {
        return id;
    }

    @Override
    ResourceLocation factoryId() {
        return factory;
    }

    /**
     * Finishes the module and registers it, so later script lines and material
     * files can use it. Call it once.
     */
    @ZenCodeType.Method
    @Override
    public void build() {
        validateBlock();
        var part = base.part();
        ScriptDefinitions.catalog().registerBlockModule(id,
            new DataModules.Block(factory, base.requiredMod(), materialDefaults,
                factoryData(), template(),
                new PartMetadata(List.of(), part.entityFactory(),
                    part.entityData(), part.blockEntity())));
        ScriptDefinitions.resources("block_module", id, resourceOptions);
        ScriptDefinitions.module(id, baseId, java.util.Set.of());
    }
}
