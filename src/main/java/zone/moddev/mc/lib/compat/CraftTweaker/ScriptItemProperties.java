package zone.moddev.mc.lib.compat.CraftTweaker;

import java.util.Optional;
import java.util.Set;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;

import net.minecraft.resources.ResourceLocation;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.common.codecs.ItemTemplate;
import zone.moddev.mc.lib.common.types.FactoryData;

/**
 * What a script item and a script item module share: stack size, durability, fire resistance and
 * container, besides the tags (item tags), events and factory data of every definition. Values
 * start from the module the builder is based on.
 */
@ZenRegister(loaders = ScriptDefinitions.LOADER)
@ZenCodeType.Name("mods.mmdlib.ItemProperties")
public abstract class ScriptItemProperties extends ScriptDefinitionProperties {
  private final ItemTemplate base;
  private int stackSize;
  private int durability;
  private boolean fireResistant;
  private Optional<ResourceLocation> container;

  ScriptItemProperties(ItemTemplate base, FactoryData factoryData, Set<ResourceLocation> tags) {
    super(tags, base.events(), factoryData);
    this.base = base;
    stackSize = base.stackSize();
    durability = base.damage();
    fireResistant = base.fireResistant();
    container = base.container();
  }

  /** How many fit in one stack, 1 to 99. */
  @ZenCodeType.Getter("stackSize")
  public int getStackSize() {
    return stackSize;
  }

  @ZenCodeType.Setter("stackSize")
  public void setStackSize(int stackSize) {
    this.stackSize = stackSize;
  }

  /** Uses before the item breaks; 0 for none. A positive value also sets the stack size to 1. */
  @ZenCodeType.Getter("durability")
  public int getDurability() {
    return durability;
  }

  @ZenCodeType.Setter("durability")
  public void setDurability(int durability) {
    this.durability = durability;
    if (durability > 0) stackSize = 1;
  }

  /** Whether the item survives fire and lava. */
  @ZenCodeType.Getter("fireResistant")
  public boolean getFireResistant() {
    return fireResistant;
  }

  @ZenCodeType.Setter("fireResistant")
  public void setFireResistant(boolean fireResistant) {
    this.fireResistant = fireResistant;
  }

  /**
   * What is left in the crafting grid after the item is used in a recipe, such as {@code
   * minecraft:bucket}; an empty string for nothing.
   */
  @ZenCodeType.Getter("container")
  public String getContainer() {
    return container.map(ResourceLocation::toString).orElse("");
  }

  @ZenCodeType.Setter("container")
  public void setContainer(String container) {
    this.container =
        container.isEmpty() ? Optional.empty() : Optional.of(ResourceLocation.parse(container));
  }

  /** The template these values describe. */
  ItemTemplate template() {
    return new ItemTemplate(
        base.name(),
        base.parent(),
        base.type(),
        stackSize,
        durability,
        fireResistant,
        base.food(),
        container,
        base.delayedUse(),
        base.colorHandler(),
        base.lore(),
        base.actions(),
        events(),
        base.group(),
        base.creativeStacks(),
        base.attributes());
  }
}
