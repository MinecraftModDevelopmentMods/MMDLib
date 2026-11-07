package zone.moddev.mc.lib.compat.CraftTweaker;

import java.util.List;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;

import net.minecraft.resources.ResourceLocation;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.common.modules.DataModules;
import zone.moddev.mc.lib.common.types.PartMetadata;

/**
 * Defines a new block module, a kind of block, from a {@code #loader mmdlib} script, like a {@code
 * data/<namespace>/block} file. Once built it can be used like any other module: materials select
 * it as a part (by its name, in script materials and material files) and {@link
 * ScriptBlockBuilder} builds standalone blocks with it.
 *
 * <pre>{@code
 * #loader mmdlib
 * import mods.mmdlib.BlockModule;
 *
 * var pillar = new BlockModule("pillar", "mmdlib:block");
 * pillar.hardness = 2;
 * pillar.addTags("minecraft:mineable/pickaxe");
 * pillar.build();
 * }</pre>
 *
 * <p>A module starts as a copy of the module it is based on: its Java factory, factory data,
 * template and block entity, but not its aliases. Change {@code factory} to use another Java
 * implementation. A material's {@code copper_pillar} gets generated art from the template of the
 * module it is based on, or else of its factory; a module with neither needs its art supplied.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.BlockModule")
public final class ScriptBlockModule extends ScriptBlockProperties {
  private final ResourceLocation id;
  private final DataModules.Block base;
  private ResourceLocation factory;
  private boolean materialDefaults;

  /** Starts module {@code name} (a path such as {@code "pillar"}) as a copy of {@code mmdlib:block}. */
  @ZenCodeType.Constructor
  public ScriptBlockModule(String name) {
    this(name, "mmdlib:block");
  }

  /** Starts module {@code name} as a copy of module {@code basedOn}, such as {@code mmdlib:stairs}. */
  @ZenCodeType.Constructor
  public ScriptBlockModule(String name, String basedOn) {
    this(mmdlibId(name, "block module"), ResourceLocation.parse(basedOn));
  }

  private final ResourceLocation baseId;

  private ScriptBlockModule(ResourceLocation id, ResourceLocation baseId) {
    this(id, baseId, ScriptDefinitions.catalog().blockModule(baseId));
  }

  private ScriptBlockModule(ResourceLocation id, ResourceLocation baseId, DataModules.Block base) {
    super(base.template(), base.factoryData());
    this.id = id;
    this.baseId = baseId;
    this.base = base;
    factory = base.factory();
    materialDefaults = base.materialDefaults();
  }

  /** The module's ID, such as {@code mmdlib:pillar}. */
  @ZenCodeType.Getter("name")
  public String getName() {
    return id.toString();
  }

  /** The Java implementation that builds the blocks, such as {@code mmdlib:stairs}. */
  @ZenCodeType.Getter("factory")
  public String getFactory() {
    return factory.toString();
  }

  @ZenCodeType.Setter("factory")
  public void setFactory(String factory) {
    var id = ResourceLocation.parse(factory);
    ScriptDefinitions.catalog().blockImplementation(id);
    this.factory = id;
  }

  /** Whether a material's stats (hardness, harvest tags...) override the template's. */
  @ZenCodeType.Getter("materialDefaults")
  public boolean getMaterialDefaults() {
    return materialDefaults;
  }

  @ZenCodeType.Setter("materialDefaults")
  public void setMaterialDefaults(boolean materialDefaults) {
    this.materialDefaults = materialDefaults;
  }

  /**
   * Registers the module. Script lines after this, and every material file, can use it.
   *
   * @throws IllegalArgumentException if a value is invalid or the name is taken
   */
  @Override
  ResourceLocation definitionId() {
    return id;
  }

  @Override
  ResourceLocation factoryId() {
    return factory;
  }

  @ZenCodeType.Method
  @Override
  public void build() {
    validateBlock();
    var part = base.part();
    ScriptDefinitions.catalog()
        .registerBlockModule(
            id,
            new DataModules.Block(
                factory,
                base.requiredMod(),
                materialDefaults,
                factoryData(),
                template(),
                new PartMetadata(List.of(), part.entityFactory(), part.entityData(), part.blockEntity())));
    ScriptDefinitions.module(id, baseId, java.util.Set.of());
  }
}
