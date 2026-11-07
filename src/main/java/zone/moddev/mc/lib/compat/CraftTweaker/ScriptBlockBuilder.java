package zone.moddev.mc.lib.compat.CraftTweaker;

import java.util.Optional;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.data.IData;

import net.minecraft.resources.ResourceLocation;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.common.types.FactoryData;
import zone.moddev.mc.lib.common.types.MaterialRuntimeSettings;
import zone.moddev.mc.lib.common.types.StandaloneBlock;

/**
 * Builds one block with its own ID from a {@code #loader mmdlib} script, like a {@code
 * mmdlib/standalone/block} file. The module decides what kind of block it is: {@code mmdlib:block}
 * (the default) for a plain cube, {@code mmdlib:stairs}, {@code mmdlib:door} and the other block
 * parts, a module another mod registers, or a {@link ScriptBlockModule}. Values start from the
 * module's template; the block is also put in {@code minecraft:mineable/pickaxe}.
 *
 * <pre>{@code
 * #loader mmdlib
 * import mods.mmdlib.BlockBuilder;
 *
 * var marble = new BlockBuilder("marble_stairs", "mmdlib:stairs");
 * marble.hardness = 1.5;
 * marble.sound = "stone";
 * marble.build();
 * }</pre>
 *
 * <p>The block is {@code mmdlib:<name>}. MMDLib generates its blockstate, models, name, loot table
 * and tags; its textures must come from {@code scripts/mmdlibdata}. The log lists the textures each
 * block needs.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.BlockBuilder")
public final class ScriptBlockBuilder extends ScriptBlockProperties {
  private final ResourceLocation id;
  private final ResourceLocation module;
  private String baseBlock = MaterialRuntimeSettings.DEFAULT.baseBlock().toString();
  private String blockSetType = MaterialRuntimeSettings.DEFAULT.blockSetType().toString();
  private FactoryData blockEntityData = FactoryData.EMPTY;

  /** Starts a plain block named {@code name}, a path such as {@code "marble"}. */
  @ZenCodeType.Constructor
  public ScriptBlockBuilder(String name) {
    this(name, "mmdlib:block");
  }

  /** Starts a block named {@code name} built by {@code module}, such as {@code mmdlib:stairs}. */
  @ZenCodeType.Constructor
  public ScriptBlockBuilder(String name, String module) {
    this(mmdlibId(name, "block"), ResourceLocation.parse(module));
  }

  private ScriptBlockBuilder(ResourceLocation id, ResourceLocation module) {
    super(ScriptDefinitions.catalog().blockModule(module).template(), FactoryData.EMPTY);
    this.id = id;
    this.module = module;
    addTags("minecraft:mineable/pickaxe");
  }

  /** The block's ID, such as {@code mmdlib:marble}. */
  @ZenCodeType.Getter("name")
  public String getName() {
    return id.toString();
  }

  /** The module that builds the block. */
  @ZenCodeType.Getter("module")
  public String getModule() {
    return module.toString();
  }

  /** The block stairs copy their behavior from; default {@code minecraft:iron_block}. */
  @ZenCodeType.Getter("baseBlock")
  public String getBaseBlock() {
    return baseBlock;
  }

  @ZenCodeType.Setter("baseBlock")
  public void setBaseBlock(String baseBlock) {
    this.baseBlock = ResourceLocation.parse(baseBlock).toString();
  }

  /**
   * How doors, trapdoors, buttons and pressure plates open and sound, such as {@code minecraft:oak}
   * (opened by hand) or {@code minecraft:iron} (needs redstone, the default).
   */
  @ZenCodeType.Getter("blockSetType")
  public String getBlockSetType() {
    return blockSetType;
  }

  @ZenCodeType.Setter("blockSetType")
  public void setBlockSetType(String blockSetType) {
    this.blockSetType = ResourceLocation.parse(blockSetType).toString();
  }

  /** Settings for the block entity, for modules that have one (machines). */
  @ZenCodeType.Setter("blockEntityData")
  public void setBlockEntityData(IData data) {
    var json = ScriptData.json(data);
    if (!json.isJsonObject())
      throw new IllegalArgumentException("blockEntityData must be a map, such as {speed: 2}");
    blockEntityData = new FactoryData(json);
  }

  /**
   * Finishes the block and queues it for registration.
   *
   * @throws IllegalArgumentException if a value is invalid
   * @throws IllegalStateException if the name was already built
   */
  @ZenCodeType.Method
  @Override
  public void build() {
    var d = MaterialRuntimeSettings.DEFAULT;
    var runtime =
        new MaterialRuntimeSettings(
            d.repairItem(),
            d.incorrectBlocks(),
            ResourceLocation.parse(baseBlock),
            ResourceLocation.parse(blockSetType),
            d.armorTexture(),
            d.blockSound(),
            d.mapColor(),
            false,
            // Script blocks use their own textures as drawn.
            false);
    ScriptDefinitions.add(
        id,
        new StandaloneBlock(module, factoryData(), Optional.of(template()), runtime, blockEntityData));
  }
}
