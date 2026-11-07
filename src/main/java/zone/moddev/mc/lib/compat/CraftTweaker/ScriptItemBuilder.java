package zone.moddev.mc.lib.compat.CraftTweaker;

import java.util.Optional;
import java.util.Set;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;

import net.minecraft.resources.ResourceLocation;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.common.types.FactoryData;
import zone.moddev.mc.lib.common.types.MaterialRuntimeSettings;
import zone.moddev.mc.lib.common.types.StandaloneItem;

/**
 * Builds one item with its own ID from a {@code #loader mmdlib} script, like a {@code
 * mmdlib/standalone/item} file. The module decides what kind of item it is: {@code mmdlib:ingot}
 * (the default) for a plain item, another MMDLib item part such as {@code mmdlib:pickaxe} or
 * {@code mmdlib:bow}, a module another mod registers, or a {@link ScriptItemModule}. Values start
 * from the module's template.
 *
 * <pre>{@code
 * #loader mmdlib
 * import mods.mmdlib.ItemBuilder;
 *
 * var wrench = new ItemBuilder("wrench");
 * wrench.stackSize = 1;
 * wrench.addTags("c:tools/wrench");
 * wrench.build();
 * }</pre>
 *
 * <p>The item is {@code mmdlib:<name>}. MMDLib generates its model, name and tags; its textures
 * must come from {@code scripts/mmdlibdata}. The log lists the textures each item needs.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.ItemBuilder")
public final class ScriptItemBuilder extends ScriptItemProperties {
  private final ResourceLocation id;
  private final ResourceLocation module;

  /** Starts a plain item named {@code name}, a path such as {@code "wrench"}. */
  @ZenCodeType.Constructor
  public ScriptItemBuilder(String name) {
    this(name, "mmdlib:ingot");
  }

  /** Starts an item named {@code name} built by {@code module}, such as {@code mmdlib:sword}. */
  @ZenCodeType.Constructor
  public ScriptItemBuilder(String name, String module) {
    this(mmdlibId(name, "item"), ResourceLocation.parse(module));
  }

  private ScriptItemBuilder(ResourceLocation id, ResourceLocation module) {
    super(
        ScriptDefinitions.catalog().itemModule(module).template(),
        FactoryData.EMPTY,
        ScriptDefinitions.itemTags().getOrDefault(module, Set.of()));
    this.id = id;
    this.module = module;
  }

  /** The item's ID, such as {@code mmdlib:wrench}. */
  @ZenCodeType.Getter("name")
  public String getName() {
    return id.toString();
  }

  /** The module that builds the item. */
  @ZenCodeType.Getter("module")
  public String getModule() {
    return module.toString();
  }

  /**
   * Finishes the item and queues it for registration.
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
            d.baseBlock(),
            d.blockSetType(),
            d.armorTexture(),
            d.blockSound(),
            d.mapColor(),
            false,
            // Script items use their own textures as drawn.
            false);
    var template = template();
    // Validate now, so the error points at this script line.
    template.toBuilder().setName(module).forMaterial(id).setRegistryName(id).build();
    ScriptDefinitions.add(
        id, new StandaloneItem(module, factoryData(), Optional.of(template), runtime), tags());
  }
}
