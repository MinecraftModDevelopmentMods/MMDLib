package zone.moddev.mc.lib.compat.CraftTweaker;

import java.util.List;
import java.util.Set;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;

import net.minecraft.resources.ResourceLocation;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.common.modules.DataModules;
import zone.moddev.mc.lib.common.types.PartMetadata;

/**
 * Defines a new item module, a kind of item, from a {@code #loader mmdlib} script, like a {@code
 * data/<namespace>/item} file. Once built it can be used like any other module: materials select
 * it as a part (by its name, in script materials and material files) and {@link ScriptItemBuilder}
 * builds standalone items with it.
 *
 * <pre>{@code
 * #loader mmdlib
 * import mods.mmdlib.ItemModule;
 *
 * var plate = new ItemModule("heavy_plate", "mmdlib:ingot");
 * plate.stackSize = 16;
 * plate.build();
 * }</pre>
 *
 * <p>A module starts as a copy of the module it is based on: its Java factory, factory data,
 * template and companion entity, but not its aliases. Change {@code factory} to use another Java
 * implementation. Its tags are given to every material's part, such as {@code copper_heavy_plate},
 * which gets generated art from the template of the module it is based on, or else its factory's.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.ItemModule")
public final class ScriptItemModule extends ScriptItemProperties {
  private final ResourceLocation id;
  private final ResourceLocation baseId;
  private final DataModules.Item base;
  private ResourceLocation factory;

  /** Starts module {@code name} (a path such as {@code "widget"}) as a copy of {@code mmdlib:ingot}. */
  @ZenCodeType.Constructor
  public ScriptItemModule(String name) {
    this(name, "mmdlib:ingot");
  }

  /** Starts module {@code name} as a copy of module {@code basedOn}, such as {@code mmdlib:sword}. */
  @ZenCodeType.Constructor
  public ScriptItemModule(String name, String basedOn) {
    this(mmdlibId(name, "item module"), ResourceLocation.parse(basedOn));
  }

  private ScriptItemModule(ResourceLocation id, ResourceLocation baseId) {
    this(id, baseId, ScriptDefinitions.catalog().itemModule(baseId));
  }

  private ScriptItemModule(ResourceLocation id, ResourceLocation baseId, DataModules.Item base) {
    super(base.template(), base.factoryData(), ScriptDefinitions.itemTags().getOrDefault(baseId, Set.of()));
    this.id = id;
    this.baseId = baseId;
    this.base = base;
    factory = base.factory();
  }

  /** The module's ID, such as {@code mmdlib:widget}. */
  @ZenCodeType.Getter("name")
  public String getName() {
    return id.toString();
  }

  /** The Java implementation that builds the items, such as {@code mmdlib:sword}. */
  @ZenCodeType.Getter("factory")
  public String getFactory() {
    return factory.toString();
  }

  @ZenCodeType.Setter("factory")
  public void setFactory(String factory) {
    var id = ResourceLocation.parse(factory);
    ScriptDefinitions.catalog().itemImplementation(id);
    this.factory = id;
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

  @ZenCodeType.Method
  @Override
  public void build() {
    var part = base.part();
    ScriptDefinitions.catalog()
        .registerItemModule(
            id,
            new DataModules.Item(
                factory,
                base.requiredMod(),
                factoryData(),
                template(),
                new PartMetadata(List.of(), part.entityFactory(), part.entityData(), part.blockEntity())));
    ScriptDefinitions.module(id, baseId, tags());
  }
}
