package zone.moddev.mc.lib.compat.CraftTweaker;

import java.util.Optional;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.data.IData;
import com.blamejared.crafttweaker_annotations.annotations.Document;

import net.minecraft.resources.ResourceLocation;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.common.types.FactoryData;
import zone.moddev.mc.lib.common.types.MaterialRuntimeSettings;
import zone.moddev.mc.lib.common.types.StandaloneBlock;

/**
 * Builds one block with its own ID from a {@code #loader mmdlib} script, like a
 * {@code mmdlib/standalone/block} file. The module decides what kind of block
 * it is: {@code mmdlib:block} (the default) for a plain cube, {@code
 * mmdlib:stairs}, {@code mmdlib:door} and the other block parts, a module
 * another mod registers, or a {@link ScriptBlockModule}. Values start from the
 * module's template; the block is also put in {@code
 * minecraft:mineable/pickaxe}.
 *
 * <p>
 * The block is {@code mmdlib:<name>}. MMDLib generates its blockstate, models,
 * name, loot table and tags; its textures must come from {@code
 * scripts/mmdlibdata}. The log lists the textures each block needs.
 *
 * @docParam this new BlockBuilder("marble")
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@Document("mods/MMDLib/definitions/BlockBuilder")
@ZenCodeType.Name("mods.mmdlib.BlockBuilder")
public final class ScriptBlockBuilder extends ScriptBlockProperties {
    private final ResourceLocation id;
    private final ResourceLocation module;
    private String baseBlock = MaterialRuntimeSettings.DEFAULT.baseBlock()
        .toString();
    private String blockSetType = MaterialRuntimeSettings.DEFAULT.blockSetType()
        .toString();
    private FactoryData blockEntityData = FactoryData.EMPTY;

    /**
     * Starts a plain block named {@code name}, a path such as {@code "marble"}.
     *
     * @param name the block's path; its ID is {@code mmdlib:<name>}
     * @docParam name "marble"
     */
    @ZenCodeType.Constructor
    public ScriptBlockBuilder(String name) {
        this(name, "mmdlib:block");
    }

    /**
     * Starts a block named {@code name} built by {@code module}, such as {@code
     * mmdlib:stairs}.
     *
     * @param name the block's path; its ID is {@code mmdlib:<name>}
     * @param module the block module's ID
     * @docParam name "marble_stairs"
     * @docParam module "mmdlib:stairs"
     */
    @ZenCodeType.Constructor
    public ScriptBlockBuilder(String name, String module) {
        this(mmdlibId(name, "block"), ResourceLocation.parse(module));
    }

    /**
     * Starts a block named {@code name} built by a module, from a reference.
     *
     * @param name the block's path; its ID is {@code mmdlib:<name>}
     * @param module the block module
     * @docParam name "marble_stairs"
     * @docParam module new BlockModuleReference("mmdlib:stairs")
     */
    @ZenCodeType.Constructor
    public ScriptBlockBuilder(String name, ScriptBlockModuleReference module) {
        this(name, module.getId());
    }

    /**
     * A reference to this block, for builder methods that name a block.
     *
     * @return the reference
     */
    @ZenCodeType.Getter("reference")
    public ScriptBlockReference getReference() {
        return new ScriptBlockReference(id.toString());
    }

    private ScriptBlockBuilder(ResourceLocation id, ResourceLocation module) {
        super(ScriptDefinitions.catalog().blockModule(module).template(),
            FactoryData.EMPTY);
        this.id = id;
        resourceOptions = ScriptDefinitions.resourceOptions("block_module",
            module);
        this.module = module;
        addTags("minecraft:mineable/pickaxe");
    }

    /**
     * The block's ID, such as {@code mmdlib:marble}.
     *
     * @return the ID
     */
    @ZenCodeType.Getter("name")
    public String getName() {
        return id.toString();
    }

    /**
     * The module that builds the block.
     *
     * @return the module's ID
     */
    @ZenCodeType.Getter("module")
    public String getModule() {
        return module.toString();
    }

    /**
     * Sets the block stairs copy their behavior from, from a reference.
     *
     * @param block the block
     * @return this builder
     * @docParam block new BlockReference("minecraft:stone")
     */
    @ZenCodeType.Method
    public ScriptBlockBuilder baseBlockFrom(ScriptBlockReference block) {
        setBaseBlock(block.getId());
        return this;
    }

    /**
     * The block stairs copy their behavior from; default {@code
     * minecraft:iron_block}.
     *
     * @return the block's ID
     */
    @ZenCodeType.Getter("baseBlock")
    public String getBaseBlock() {
        return baseBlock;
    }

    /**
     * Sets the block stairs copy their behavior from.
     *
     * @param baseBlock the block's ID
     * @docParam baseBlock "minecraft:stone"
     */
    @ZenCodeType.Setter("baseBlock")
    public void setBaseBlock(String baseBlock) {
        this.baseBlock = ResourceLocation.parse(baseBlock).toString();
    }

    /**
     * How doors, trapdoors, buttons and pressure plates open and sound, such as
     * {@code minecraft:oak} (opened by hand) or {@code minecraft:iron} (needs
     * redstone, the default).
     *
     * @return the block set type's ID
     */
    @ZenCodeType.Getter("blockSetType")
    public String getBlockSetType() {
        return blockSetType;
    }

    /**
     * Sets how doors, trapdoors, buttons and pressure plates open and sound,
     * such as {@code minecraft:oak} (opened by hand) or {@code minecraft:iron}
     * (needs redstone).
     *
     * @param blockSetType the block set type's ID
     * @docParam blockSetType "minecraft:iron"
     */
    @ZenCodeType.Setter("blockSetType")
    public void setBlockSetType(String blockSetType) {
        this.blockSetType = ResourceLocation.parse(blockSetType).toString();
    }

    /**
     * Settings for the block entity, for modules that have one (machines).
     *
     * @param data the settings
     * @docParam data {speed: 2}
     */
    @ZenCodeType.Setter("blockEntityData")
    public void setBlockEntityData(IData data) {
        var json = ScriptData.json(data);
        if (!json.isJsonObject())
            throw new IllegalArgumentException(
                "blockEntityData must be a map, such as {speed: 2}");
        blockEntityData = new FactoryData(json);
    }

    /**
     * Finishes the block and queues it for registration.
     *
     * @throws IllegalArgumentException if a value is invalid
     * @throws IllegalStateException if the name was already built
     */
    @Override
    ResourceLocation definitionId() {
        return id;
    }

    @Override
    ResourceLocation factoryId() {
        return ScriptDefinitions.catalog().blockModule(module).factory();
    }

    /**
     * Finishes the block and queues it for registration. Call it once.
     */
    @ZenCodeType.Method
    @Override
    public void build() {
        validateBlock();
        var d = MaterialRuntimeSettings.DEFAULT;
        var runtime = new MaterialRuntimeSettings(d.repairItem(),
            d.incorrectBlocks(), ResourceLocation.parse(baseBlock),
            ResourceLocation.parse(blockSetType), d.armorTexture(),
            d.blockSound(), d.mapColor(), false,
            // Script blocks use their own textures as drawn.
            false);
        ScriptDefinitions.add(id, new StandaloneBlock(module, factoryData(),
            Optional.of(template()), runtime, blockEntityData));
        ScriptDefinitions.resources("block", id, resourceOptions);
    }
}
