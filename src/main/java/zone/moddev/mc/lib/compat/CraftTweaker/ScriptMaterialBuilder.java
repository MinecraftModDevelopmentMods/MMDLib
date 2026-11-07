package zone.moddev.mc.lib.compat.CraftTweaker;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;

import net.minecraft.resources.ResourceLocation;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.common.builders.MaterialBuilder;
import zone.moddev.mc.lib.common.types.MaterialPart;
import zone.moddev.mc.lib.common.types.MaterialProperties;
import zone.moddev.mc.lib.common.types.MaterialRuntimeSettings;
import zone.moddev.mc.lib.common.types.MaterialSelection;
import zone.moddev.mc.lib.common.types.PartSelection;

/**
 * Builds an MMDLib material from a {@code #loader mmdlib} script. Plain values are properties;
 * collections have an add and a remove function, where the name {@code "all"} means every entry.
 * Nothing is registered until {@link #build()}.
 *
 * <pre>{@code
 * #loader mmdlib
 * import mods.mmdlib.MaterialBuilder;
 *
 * var copper = new MaterialBuilder("copper");
 * copper.type = "metal";
 * copper.color = 0xFFFF9B40;
 * copper.hardness = 3.5;
 * copper.removeItems("all").addItems("ingot", "nugget", "dust");
 * copper.removeBlocks("door");
 * copper.hasFluid = false;
 * copper.build();
 * }</pre>
 *
 * <p>A new builder matches a material file that sets nothing: every item and block part, and a
 * fluid. Content is registered as {@code mmdlib:<name>_<part>}.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.MaterialBuilder")
public final class ScriptMaterialBuilder {
  private static final String ALL = "all";

  private final ResourceLocation name;
  private final Set<MaterialPart> availableItems;
  private final Set<MaterialPart> availableBlocks;
  private final Set<MaterialPart> availableEntities;
  private final Set<MaterialPart> items;
  private final Set<MaterialPart> blocks;
  private final Set<ResourceLocation> harvestTags = new LinkedHashSet<>();
  private final Map<String, Double> special = new LinkedHashMap<>();
  private String type = MaterialProperties.DEFAULT.type();
  private int color = MaterialProperties.DEFAULT.color();
  private double hardness;
  private double strength;
  private double magic;
  private double damage;
  private boolean hasFluid;
  private ResourceLocation fluid;

  /**
   * Starts a material. The name is a path such as {@code "copper"}; {@code "mmdlib:copper"} is also
   * accepted, but no other namespace.
   */
  @ZenCodeType.Constructor
  public ScriptMaterialBuilder(String name) {
    var id = ResourceLocation.parse(name.contains(":") ? name : MMDLib.MODID + ":" + name);
    if (!id.getNamespace().equals(MMDLib.MODID))
      throw new IllegalArgumentException(
          "Script materials are registered in the "
              + MMDLib.MODID
              + " namespace; use \""
              + id.getPath()
              + "\" instead of \""
              + name
              + "\"");
    this.name = id;
    var snapshot = ScriptDefinitions.catalog().snapshot();
    var parts = snapshot.parts();
    var defaults = MaterialSelection.defaults(snapshot);
    availableItems = parts.items();
    availableBlocks = parts.blocks();
    availableEntities = parts.entities();
    items = new LinkedHashSet<>(defaults.items());
    blocks = new LinkedHashSet<>(defaults.blocks());
    hasFluid = defaults.hasFluid();
    fluid = defaults.fluidModule();
  }

  /** The material's ID, such as {@code mmdlib:copper}. */
  @ZenCodeType.Getter("name")
  public String getName() {
    return name.toString();
  }

  /** The classification, such as {@code metal} or {@code gem}; default {@code generic}. */
  @ZenCodeType.Getter("type")
  public String getType() {
    return type;
  }

  @ZenCodeType.Setter("type")
  public void setType(String type) {
    this.type = type;
  }

  /** The ARGB color used to tint textures; default opaque white. */
  @ZenCodeType.Getter("color")
  public int getColor() {
    return color;
  }

  @ZenCodeType.Setter("color")
  public void setColor(int color) {
    this.color = color;
  }

  /** Mining speed, armor toughness and related values; default 0. */
  @ZenCodeType.Getter("hardness")
  public double getHardness() {
    return hardness;
  }

  @ZenCodeType.Setter("hardness")
  public void setHardness(double hardness) {
    this.hardness = hardness;
  }

  /** Tool and armor durability; default 0. */
  @ZenCodeType.Getter("strength")
  public double getStrength() {
    return strength;
  }

  @ZenCodeType.Setter("strength")
  public void setStrength(double strength) {
    this.strength = strength;
  }

  /** Magic affinity, which drives enchantability; default 0. */
  @ZenCodeType.Getter("magic")
  public double getMagic() {
    return magic;
  }

  @ZenCodeType.Setter("magic")
  public void setMagic(double magic) {
    this.magic = magic;
  }

  /** The tool tier's base attack-damage bonus; default 0. */
  @ZenCodeType.Getter("damage")
  public double getDamage() {
    return damage;
  }

  @ZenCodeType.Setter("damage")
  public void setDamage(double damage) {
    this.damage = damage;
  }

  /** Whether the material gets a fluid; default true. */
  @ZenCodeType.Getter("hasFluid")
  public boolean getHasFluid() {
    return hasFluid;
  }

  @ZenCodeType.Setter("hasFluid")
  public void setHasFluid(boolean hasFluid) {
    this.hasFluid = hasFluid;
  }

  /** The fluid module that builds the fluid; default {@code mmdlib:molten}. */
  @ZenCodeType.Getter("fluid")
  public String getFluid() {
    return fluid.toString();
  }

  @ZenCodeType.Setter("fluid")
  public void setFluid(String fluid) {
    this.fluid = ResourceLocation.parse(fluid);
  }

  /** The selected item parts, by short ID. */
  @ZenCodeType.Getter("items")
  public String[] getItems() {
    return ids(items);
  }

  /** Adds item parts by name, such as {@code "ingot"}; {@code "all"} adds every item part. */
  @ZenCodeType.Method
  public ScriptMaterialBuilder addItems(String... names) {
    items.addAll(resolve(names, availableItems, "item"));
    return this;
  }

  /** Removes item parts by name; {@code "all"} removes every item part. */
  @ZenCodeType.Method
  public ScriptMaterialBuilder removeItems(String... names) {
    items.removeAll(resolve(names, availableItems, "item"));
    return this;
  }

  /** The selected block parts, by short ID. */
  @ZenCodeType.Getter("blocks")
  public String[] getBlocks() {
    return ids(blocks);
  }

  /** Adds block parts by name, such as {@code "block"}; {@code "all"} adds every block part. */
  @ZenCodeType.Method
  public ScriptMaterialBuilder addBlocks(String... names) {
    blocks.addAll(resolve(names, availableBlocks, "block"));
    return this;
  }

  /** Removes block parts by name; {@code "all"} removes every block part. */
  @ZenCodeType.Method
  public ScriptMaterialBuilder removeBlocks(String... names) {
    blocks.removeAll(resolve(names, availableBlocks, "block"));
    return this;
  }

  /** The harvest tags, such as {@code minecraft:needs_iron_tool}. */
  @ZenCodeType.Getter("harvestTags")
  public String[] getHarvestTags() {
    return harvestTags.stream().map(ResourceLocation::toString).toArray(String[]::new);
  }

  /**
   * Adds block tags the material's blocks are put in, such as {@code "minecraft:needs_iron_tool"}.
   */
  @ZenCodeType.Method
  public ScriptMaterialBuilder addHarvestTags(String... tags) {
    for (var tag : tags) {
      if (tag.equals(ALL))
        throw new IllegalArgumentException("\"all\" can only remove harvest tags, not add them");
      harvestTags.add(ResourceLocation.parse(tag));
    }
    return this;
  }

  /** Removes harvest tags; {@code "all"} removes every tag. */
  @ZenCodeType.Method
  public ScriptMaterialBuilder removeHarvestTags(String... tags) {
    for (var tag : tags)
      if (tag.equals(ALL)) harvestTags.clear();
      else harvestTags.remove(ResourceLocation.parse(tag));
    return this;
  }

  /**
   * Sets a special stat, such as {@code blast_resistance}, {@code temperature} or {@code density};
   * an existing value is replaced.
   */
  @ZenCodeType.Method
  public ScriptMaterialBuilder addSpecial(String key, double value) {
    if (key.equals(ALL)) throw new IllegalArgumentException("\"all\" is not a special stat name");
    special.put(key, value);
    return this;
  }

  /** Removes special stats by key; {@code "all"} removes every one. */
  @ZenCodeType.Method
  public ScriptMaterialBuilder removeSpecial(String... keys) {
    for (var key : keys)
      if (key.equals(ALL)) special.clear();
      else special.remove(key);
    return this;
  }

  /**
   * Finishes the material and queues it for registration. Call it once, from a {@code #loader
   * mmdlib} script; later changes to this builder have no effect.
   *
   * @throws IllegalArgumentException if a value is invalid, such as a negative hardness
   * @throws IllegalStateException if the name was already built
   */
  @ZenCodeType.Method
  public void build() {
    var material =
        MaterialBuilder.create()
            .setName(name)
            .setProperties(
                new MaterialProperties(
                    type,
                    color,
                    hardness,
                    strength,
                    magic,
                    damage,
                    Set.copyOf(harvestTags),
                    Map.copyOf(special)))
            // Projectile entities follow their items: a selected arrow always gets its entity.
            .setSelection(
                new MaterialSelection(
                    items, blocks, availableEntities, hasFluid, Set.of(), Set.of(), fluid))
            // Generated textures are colored already (see ScriptMaterialResources), and armor
            // layers are generated under the material's own name.
            .setRuntime(runtime())
            .build();
    ScriptDefinitions.add(material);
  }

  private MaterialRuntimeSettings runtime() {
    var d = MaterialRuntimeSettings.DEFAULT;
    return new MaterialRuntimeSettings(
        d.repairItem(),
        d.incorrectBlocks(),
        d.baseBlock(),
        d.blockSetType(),
        name,
        d.blockSound(),
        d.mapColor(),
        false,
        false);
  }

  private static Set<MaterialPart> resolve(
      String[] names, Set<MaterialPart> available, String section) {
    for (var name : names)
      if (name.startsWith("!"))
        throw new IllegalArgumentException(
            "Use remove instead of \"" + name + "\" to leave out a " + section + " part");
    try {
      return PartSelection.parse(String.join(" ", names), available);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Unknown " + section + " part: " + ex.getMessage(), ex);
    }
  }

  private static String[] ids(Set<MaterialPart> parts) {
    return parts.stream().map(MaterialPart::id).sorted().toArray(String[]::new);
  }
}
