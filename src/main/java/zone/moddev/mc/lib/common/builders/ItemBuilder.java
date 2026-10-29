package zone.moddev.mc.lib.common.builders;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.UseAnim;

import zone.moddev.mc.lib.common.types.MMDItem;
import zone.moddev.mc.lib.util.StackContext;

/**
 * Builds {@link zone.moddev.mc.lib.common.types.MMDItem} definitions. Start from {@link #create()};
 * every setter returns a new builder. An item is named {@code <material>_<template>} unless {@link
 * #setRegistryName} fixes its ID. Templates are bound to a material by the planner before {@link
 * #build()}.
 */
public final class ItemBuilder extends Builder<MMDItem, ItemBuilder> {
  /** The empty builder every chain starts from. */
  public static final ItemBuilder INSTANCE = new ItemBuilder(Map.of());

  private enum Key {
    NAME,
    REGISTRY_NAME,
    MATERIAL,
    PARENT,
    TYPE,
    STACK_SIZE,
    DAMAGE,
    FIRE_RESISTANT,
    FOOD,
    CONTAINER,
    USE_TIME,
    USE_ANIM,
    USE_FINISH,
    COLOR,
    LORE,
    ACTIONS,
    EVENTS,
    CREATIVE,
    ATTRIBUTES
  }

  private final Map<Key, Object> values;

  private ItemBuilder(Map<Key, Object> values) {
    this.values = Map.copyOf(values);
  }

  /** The empty builder. */
  public static ItemBuilder create() {
    return INSTANCE;
  }

  private ItemBuilder with(Key key, Object value) {
    var copy = new EnumMap<Key, Object>(Key.class);
    copy.putAll(values);
    copy.put(key, Objects.requireNonNull(value, key.name()));
    return new ItemBuilder(copy);
  }

  @SuppressWarnings("unchecked")
  private <T> T value(Key key, T fallback) {
    return (T) values.getOrDefault(key, fallback);
  }

  @Override
  public ItemBuilder setName(ResourceLocation name) {
    return with(Key.NAME, name);
  }

  @Override
  public ItemBuilder setName(String name) {
    return setName(ResourceLocation.parse(name));
  }

  /** The template (part) name, or null if not set. */
  public ResourceLocation getTemplateName() {
    return value(Key.NAME, null);
  }

  /** The owning material, or null if not set. */
  public ResourceLocation getMaterialName() {
    return value(Key.MATERIAL, null);
  }

  /** Binds the item to {@code material}. */
  public ItemBuilder forMaterial(ResourceLocation material) {
    return with(Key.MATERIAL, material);
  }

  /** As {@link #forMaterial(ResourceLocation)}, parsing {@code material}. */
  public ItemBuilder forMaterial(String material) {
    return forMaterial(ResourceLocation.parse(material));
  }

  /** Fixed registry ID for a standalone definition, instead of {@code <material>_<template>}. */
  public ItemBuilder setRegistryName(ResourceLocation id) {
    return with(Key.REGISTRY_NAME, id);
  }

  @Override
  public ResourceLocation getRegistryName() {
    if (values.containsKey(Key.REGISTRY_NAME)) return value(Key.REGISTRY_NAME, null);
    ResourceLocation template = getTemplateName();
    ResourceLocation material = getMaterialName();
    if (template == null || material == null) return null;
    return ResourceLocation.fromNamespaceAndPath(
        material.getNamespace(), material.getPath() + "_" + template.getPath());
  }

  @Override
  public ItemBuilder setParent(ResourceLocation parent) {
    return with(Key.PARENT, parent);
  }

  /**
   * Sets the type: the ID of the Java implementation that creates the item.
   *
   * @throws IllegalArgumentException if {@code type} is blank
   */
  public ItemBuilder setType(String type) {
    if (type == null || type.isBlank())
      throw new IllegalArgumentException("Item type must not be blank");
    return with(Key.TYPE, type);
  }

  /** Sets the most items one stack holds. */
  public ItemBuilder setMaxStackSize(int size) {
    if (size < 1 || size > 99)
      throw new IllegalArgumentException("Stack size must be between 1 and 99");
    return with(Key.STACK_SIZE, size);
  }

  /** Sets the durability; an item with durability does not stack. */
  public ItemBuilder setMaxDamage(int damage) {
    if (damage < 0) throw new IllegalArgumentException("Damage must not be negative");
    ItemBuilder next = with(Key.DAMAGE, damage);
    return damage > 0 ? next.with(Key.STACK_SIZE, 1) : next;
  }

  /** Sets whether the dropped item survives fire and lava. */
  public ItemBuilder setFireResistant(boolean resistant) {
    return with(Key.FIRE_RESISTANT, resistant);
  }

  /** Sets the food properties by ID. */
  public ItemBuilder setFood(ResourceLocation food) {
    return with(Key.FOOD, food);
  }

  /** Sets the item left behind after crafting with this one, as a bucket is for milk. */
  public ItemBuilder setContainerItem(ResourceLocation item) {
    return with(Key.CONTAINER, item);
  }

  /**
   * Sets how many ticks using the item takes.
   *
   * @throws IllegalArgumentException if negative
   */
  public ItemBuilder setUseTime(int ticks) {
    if (ticks < 0) throw new IllegalArgumentException("Use time must not be negative");
    return with(Key.USE_TIME, ticks);
  }

  /** Sets the animation shown while the item is used. */
  public ItemBuilder setUseAnim(UseAnim animation) {
    return with(Key.USE_ANIM, animation);
  }

  /** Identifier of the finish action to resolve when material content is instantiated. */
  public ItemBuilder setUseFinishMode(ResourceLocation action) {
    return with(Key.USE_FINISH, action);
  }

  /**
   * Sets the name of the client color handler that tints the item.
   *
   * @throws IllegalArgumentException if blank
   */
  public ItemBuilder setColorHandler(String handler) {
    if (handler == null || handler.isBlank())
      throw new IllegalArgumentException("Color handler must not be blank");
    return with(Key.COLOR, handler);
  }

  /** Lore is retained as text; text-component parsing is the loader's responsibility. */
  public ItemBuilder setLore(List<String> lore) {
    return with(Key.LORE, List.copyOf(lore));
  }

  /**
   * Sets the tool actions the item can perform, e.g. {@code axe_strip}.
   *
   * @throws IllegalArgumentException if an action is blank
   */
  public ItemBuilder setToolActions(String[] actions) {
    var copy = new java.util.LinkedHashSet<String>();
    for (String action : actions) {
      if (action == null || action.isBlank())
        throw new IllegalArgumentException("Tool action must not be blank");
      copy.add(action);
    }
    return with(Key.ACTIONS, Set.copyOf(copy));
  }

  @Override
  public ItemBuilder setEventMap(Map<String, ResourceLocation> events) {
    events.forEach(
        (event, action) -> {
          if (event.isBlank()) throw new IllegalArgumentException("Event name must not be blank");
          Objects.requireNonNull(action, "event action");
        });
    return with(Key.EVENTS, Map.copyOf(events));
  }

  /** Adds a stack to show in the given creative tabs. */
  public ItemBuilder withCreativeMenuStack(StackContext stack, String[] tabs) {
    Objects.requireNonNull(stack, "stack");
    List<MMDItem.CreativeStack> entries = new ArrayList<>(value(Key.CREATIVE, List.of()));
    entries.add(
        new MMDItem.CreativeStack(stack.item, stack.tag, stack.count, List.of(tabs.clone())));
    return with(Key.CREATIVE, List.copyOf(entries));
  }

  /** Sets the attribute modifiers applied while the item is held or worn. */
  public ItemBuilder setAttributeModifiers(
      List<zone.moddev.mc.lib.common.types.AttributeModifierDefinition> modifiers) {
    return with(Key.ATTRIBUTES, List.copyOf(modifiers));
  }

  /** Binds the item to {@code material} and builds it. */
  public MMDItem buildFor(ResourceLocation material) {
    return forMaterial(material).build();
  }

  @Override
  public MMDItem build() {
    if (getTemplateName() == null) throw new IllegalStateException("Item template needs a name");
    if (getMaterialName() == null)
      throw new IllegalStateException("Item template must be bound to a material");
    return new MMDItem(
        getRegistryName(),
        getTemplateName(),
        getMaterialName(),
        value(Key.PARENT, null),
        value(Key.TYPE, "item"),
        value(Key.STACK_SIZE, 64),
        value(Key.DAMAGE, 0),
        value(Key.FIRE_RESISTANT, false),
        value(Key.FOOD, null),
        value(Key.CONTAINER, null),
        value(Key.USE_TIME, 0),
        value(Key.USE_ANIM, UseAnim.NONE),
        value(Key.USE_FINISH, null),
        value(Key.COLOR, null),
        value(Key.LORE, List.of()),
        value(Key.ACTIONS, Set.of()),
        value(Key.EVENTS, Map.of()),
        value(Key.CREATIVE, List.of()),
        value(Key.ATTRIBUTES, List.of()));
  }
}
