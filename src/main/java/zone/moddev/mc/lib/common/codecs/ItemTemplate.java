package zone.moddev.mc.lib.common.codecs;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.UseAnim;

import zone.moddev.mc.lib.common.builders.ItemBuilder;
import zone.moddev.mc.lib.common.types.AttributeModifierDefinition;
import zone.moddev.mc.lib.util.StackContext;

/** Data-only item template schema; material binding is performed after decoding. */
public record ItemTemplate(
    Optional<ResourceLocation> name,
    Optional<ResourceLocation> parent,
    String type,
    int stackSize,
    int damage,
    boolean fireResistant,
    Optional<ResourceLocation> food,
    Optional<ResourceLocation> container,
    Optional<DelayedUse> delayedUse,
    Optional<String> colorHandler,
    List<String> lore,
    List<String> actions,
    Map<String, ResourceLocation> events,
    Optional<String> group,
    List<CreativeStack> creativeStacks,
    List<AttributeModifierDefinition> attributes) {
  /**
   * An item that takes time to use, like food.
   *
   * @param duration use time in ticks
   * @param animation the animation shown while using it
   * @param onComplete the action run when use finishes, if any
   */
  public record DelayedUse(int duration, UseAnim animation, Optional<ResourceLocation> onComplete) {
    private static final Codec<UseAnim> ANIMATION =
        Codec.STRING.comapFlatMap(
            value -> {
              try {
                return DataResult.success(
                    UseAnim.valueOf(value.toUpperCase(java.util.Locale.ROOT)));
              } catch (IllegalArgumentException ex) {
                return DataResult.error(() -> "Unknown use animation: " + value);
              }
            },
            value -> value.name().toLowerCase(java.util.Locale.ROOT));
    public static final Codec<DelayedUse> CODEC =
        RecordCodecBuilder.create(
            i ->
                i.group(
                        Codec.intRange(0, Integer.MAX_VALUE)
                            .fieldOf("duration")
                            .forGetter(DelayedUse::duration),
                        ANIMATION.fieldOf("animation").forGetter(DelayedUse::animation),
                        ResourceLocation.CODEC
                            .optionalFieldOf("on_complete")
                            .forGetter(DelayedUse::onComplete))
                    .apply(i, DelayedUse::new));
  }

  /**
   * A stack to show in creative tabs.
   *
   * @param item the item, or this item when empty
   * @param tag extra NBT data, if any
   * @param count the stack size, 1-99
   * @param tabs the creative tabs to show it in
   */
  public record CreativeStack(
      Optional<ResourceLocation> item, Optional<CompoundTag> tag, int count, List<String> tabs) {
    public static final Codec<CreativeStack> CODEC =
        RecordCodecBuilder.create(
            i ->
                i.group(
                        ResourceLocation.CODEC
                            .optionalFieldOf("item")
                            .forGetter(CreativeStack::item),
                        CompoundTag.CODEC.optionalFieldOf("tag").forGetter(CreativeStack::tag),
                        Codec.intRange(1, 99)
                            .optionalFieldOf("count", 1)
                            .forGetter(CreativeStack::count),
                        Codec.STRING.listOf().fieldOf("tabs").forGetter(CreativeStack::tabs))
                    .apply(i, CreativeStack::new));
  }

  private static final Codec<ItemTemplate> FIELDS =
      RecordCodecBuilder.create(
          i ->
              i.group(
                      ResourceLocation.CODEC.optionalFieldOf("name").forGetter(ItemTemplate::name),
                      ResourceLocation.CODEC
                          .optionalFieldOf("parent")
                          .forGetter(ItemTemplate::parent),
                      Codec.STRING.optionalFieldOf("type", "item").forGetter(ItemTemplate::type),
                      Codec.intRange(1, 99)
                          .optionalFieldOf("max_stack_size", 64)
                          .forGetter(ItemTemplate::stackSize),
                      Codec.intRange(0, Integer.MAX_VALUE)
                          .optionalFieldOf("max_damage", 0)
                          .forGetter(ItemTemplate::damage),
                      Codec.BOOL
                          .optionalFieldOf("fire_resistant", false)
                          .forGetter(ItemTemplate::fireResistant),
                      ResourceLocation.CODEC.optionalFieldOf("food").forGetter(ItemTemplate::food),
                      ResourceLocation.CODEC
                          .optionalFieldOf("container")
                          .forGetter(ItemTemplate::container),
                      DelayedUse.CODEC
                          .optionalFieldOf("delayed_use")
                          .forGetter(ItemTemplate::delayedUse),
                      Codec.STRING
                          .optionalFieldOf("color_handler")
                          .forGetter(ItemTemplate::colorHandler),
                      Codec.STRING
                          .listOf()
                          .optionalFieldOf("lore", List.of())
                          .forGetter(ItemTemplate::lore),
                      Codec.STRING
                          .listOf()
                          .optionalFieldOf("tool_actions", List.of())
                          .forGetter(ItemTemplate::actions),
                      Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC)
                          .optionalFieldOf("events", Map.of())
                          .forGetter(ItemTemplate::events),
                      Codec.STRING.optionalFieldOf("group").forGetter(ItemTemplate::group),
                      CreativeStack.CODEC
                          .listOf()
                          .optionalFieldOf("creative_menu_stacks", List.of())
                          .forGetter(ItemTemplate::creativeStacks),
                      AttributeModifierDefinition.CODEC
                          .listOf()
                          .optionalFieldOf("attribute_modifiers", List.of())
                          .forGetter(ItemTemplate::attributes))
                  .apply(i, ItemTemplate::new));
  public static final Codec<ItemTemplate> CODEC =
      FIELDS.validate(
          value -> {
            if (value.group.isPresent() && !value.creativeStacks.isEmpty())
              return DataResult.error(() -> "Cannot combine group and creative_menu_stacks");
            try {
              value.toBuilder();
              return DataResult.success(value);
            } catch (IllegalArgumentException ex) {
              return DataResult.error(ex::getMessage);
            }
          });

  /** An item builder holding this template's values. */
  public ItemBuilder toBuilder() {
    ItemBuilder b =
        ItemBuilder.create()
            .setType(type)
            .setMaxStackSize(stackSize)
            .setMaxDamage(damage)
            .setFireResistant(fireResistant)
            .setLore(lore)
            .setToolActions(actions.toArray(String[]::new))
            .setEventMap(events)
            .setAttributeModifiers(attributes);
    if (name.isPresent()) b = b.setName(name.get());
    if (parent.isPresent()) b = b.setParent(parent.get());
    if (food.isPresent()) b = b.setFood(food.get());
    if (container.isPresent()) b = b.setContainerItem(container.get());
    if (colorHandler.isPresent()) b = b.setColorHandler(colorHandler.get());
    if (delayedUse.isPresent()) {
      var use = delayedUse.get();
      b = b.setUseTime(use.duration).setUseAnim(use.animation);
      if (use.onComplete.isPresent()) b = b.setUseFinishMode(use.onComplete.get());
    }
    if (group.isPresent())
      b = b.withCreativeMenuStack(new StackContext(null), new String[] {group.get()});
    for (var entry : creativeStacks) {
      var stack = new StackContext(entry.item.orElse(null)).withCount(entry.count);
      if (entry.tag.isPresent()) stack = stack.withTag(entry.tag.get());
      b = b.withCreativeMenuStack(stack, entry.tabs.toArray(String[]::new));
    }
    return b;
  }
}
