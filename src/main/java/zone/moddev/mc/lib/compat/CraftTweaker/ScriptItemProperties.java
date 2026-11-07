package zone.moddev.mc.lib.compat.CraftTweaker;

import java.util.Optional;
import java.util.Set;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;

import net.minecraft.resources.ResourceLocation;

import org.openzen.zencode.java.ZenCodeType;

import zone.moddev.mc.lib.api.MaterialEventTypes;
import zone.moddev.mc.lib.common.codecs.ItemTemplate;
import zone.moddev.mc.lib.common.types.FactoryData;

/**
 * What a script item and a script item module share: stack size, durability, fire resistance,
 * container, use time and script handlers, besides the tags (item tags), events and factory data of
 * every definition. Values
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
  private int useDuration;
  private String useAnimation;

  ScriptItemProperties(ItemTemplate base, FactoryData factoryData, Set<ResourceLocation> tags) {
    super(tags, base.events(), factoryData);
    this.base = base;
    stackSize = base.stackSize();
    durability = base.damage();
    fireResistant = base.fireResistant();
    container = base.container();
    useDuration = base.delayedUse().map(ItemTemplate.DelayedUse::duration).orElse(0);
    useAnimation =
        base.delayedUse()
            .map(use -> use.animation().name().toLowerCase(java.util.Locale.ROOT))
            .orElse("none");
  }

  /**
   * Ticks the item takes to use when right-clicked, like eating or drawing a bow; 0 (the default)
   * for an item that is not used over time. Plain items need it for the {@code onUse...} handlers;
   * items that are already used over time (food, bows) have their own.
   */
  @ZenCodeType.Getter("useDuration")
  public int getUseDuration() {
    return useDuration;
  }

  @ZenCodeType.Setter("useDuration")
  public void setUseDuration(int useDuration) {
    this.useDuration = useDuration;
  }

  /**
   * How the player holds the item while using it: {@code none}, {@code eat}, {@code drink}, {@code
   * block}, {@code bow}, {@code spear}, {@code crossbow}, {@code spyglass}, {@code toot_horn} or
   * {@code brush}.
   */
  @ZenCodeType.Getter("useAnimation")
  public String getUseAnimation() {
    return useAnimation;
  }

  @ZenCodeType.Setter("useAnimation")
  public void setUseAnimation(String useAnimation) {
    net.minecraft.world.item.UseAnim.valueOf(useAnimation.toUpperCase(java.util.Locale.ROOT));
    this.useAnimation = useAnimation;
  }

  /** Runs {@code handler} when an entity starts using the item; it can change the duration or cancel. */
  @ZenCodeType.Method
  public ScriptItemProperties onUseStart(ScriptItemUseHandler handler) {
    return use(MaterialEventTypes.ITEM_USE_START, handler);
  }

  /** Runs {@code handler} each tick the item is used; it can cancel. */
  @ZenCodeType.Method
  public ScriptItemProperties onUseTick(ScriptItemUseHandler handler) {
    return use(MaterialEventTypes.ITEM_USE_TICK, handler);
  }

  /** Runs {@code handler} when use stops early, like releasing a bow. */
  @ZenCodeType.Method
  public ScriptItemProperties onUseStop(ScriptItemUseHandler handler) {
    return use(MaterialEventTypes.ITEM_USE_STOP, handler);
  }

  /** Runs {@code handler} when use finishes; it can replace the stack left in hand. */
  @ZenCodeType.Method
  public ScriptItemProperties onUseFinish(ScriptItemUseHandler handler) {
    return use(MaterialEventTypes.ITEM_USE_FINISH, handler);
  }

  private ScriptItemProperties use(
      zone.moddev.mc.lib.api.MaterialEvent<zone.moddev.mc.lib.api.MaterialEventContexts.ItemUse> event,
      ScriptItemUseHandler handler) {
    hook(event, handler, (h, use) -> h.handle(new ScriptItemUseContext(use)));
    return this;
  }

  /** Runs {@code handler} on the server after a melee hit with the item has dealt damage. */
  @ZenCodeType.Method
  public ScriptItemProperties onHit(ScriptHitHandler handler) {
    hook(MaterialEventTypes.ITEM_HIT, handler, (h, hit) -> h.handle(new ScriptHitContext(hit)));
    return this;
  }

  /**
   * Runs {@code handler} on the server when a player holding the item is about to break a block; it
   * can cancel, or {@code mine} more blocks (a 3x3 pickaxe).
   */
  @ZenCodeType.Method
  public ScriptItemProperties onBreakBlock(ScriptBreakHandler handler) {
    hook(
        MaterialEventTypes.ITEM_BREAK_BLOCK,
        handler,
        (h, broken) -> h.handle(new ScriptBreakContext(broken)));
    return this;
  }

  /**
   * Runs {@code handler} when a player right-clicks with the item on air, a block or an entity, on
   * both sides, alongside the item's own use.
   */
  @ZenCodeType.Method
  public ScriptItemProperties onRightClick(ScriptItemClickHandler handler) {
    hook(
        MaterialEventTypes.TOOL_RIGHT_CLICK,
        handler,
        (h, click) -> h.handle(new ScriptItemClickContext(click)));
    return this;
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

  /** Configure only an item/module whose selected implementation is mmdlib:tool. */
  @ZenCodeType.Method
  public ScriptItemProperties configureTool(ScriptToolSettings settings) {
    var factory = this instanceof ScriptItemModule module
        ? module.getFactory()
        : ScriptDefinitions.catalog().itemModule(
            ResourceLocation.parse(((ScriptItemBuilder) this).getModule())).factory().toString();
    if (!factory.equals("mmdlib:tool"))
      throw new IllegalArgumentException("configureTool requires factory mmdlib:tool, not " + factory);
    var merged = factoryData().json().getAsJsonObject();
    settings.json().entrySet().forEach(entry -> merged.add(entry.getKey(), entry.getValue()));
    new FactoryData(merged).decode(zone.moddev.mc.lib.common.runtime.StandardItems.ToolSettings.CODEC)
        .getOrThrow(message -> new IllegalArgumentException("Invalid tool settings: " + message));
    setFactoryData(com.blamejared.crafttweaker.api.data.converter.JSONConverter.convert(merged));
    return this;
  }

  @ZenCodeType.Method
  public ScriptItemProperties remainder(ScriptItemReference item) {
    setContainer(item.getId()); return this;
  }

  @ZenCodeType.Method
  public ScriptItemProperties stacksTo(int size) { setStackSize(size); return this; }

  @ZenCodeType.Method
  public ScriptItemProperties craftRemainder(String item) { setContainer(item); return this; }

  @ZenCodeType.Method
  public ScriptItemProperties craftRemainder(ScriptItemReference item) {
    return craftRemainder(item.getId());
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
        useDuration > 0
            ? Optional.of(
                new ItemTemplate.DelayedUse(
                    useDuration,
                    net.minecraft.world.item.UseAnim.valueOf(
                        useAnimation.toUpperCase(java.util.Locale.ROOT)),
                    base.delayedUse().flatMap(ItemTemplate.DelayedUse::onComplete)))
            : Optional.empty(),
        base.colorHandler(),
        base.lore(),
        base.actions(),
        events(),
        base.group(),
        base.creativeStacks(),
        base.attributes());
  }
}
