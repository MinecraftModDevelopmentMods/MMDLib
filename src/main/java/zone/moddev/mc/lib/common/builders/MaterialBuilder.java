package zone.moddev.mc.lib.common.builders;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

import zone.moddev.mc.lib.common.types.*;

/**
 * Builds {@link zone.moddev.mc.lib.common.types.MMDMaterial} definitions in code, as an alternative
 * to material JSON. Start from {@link #create()} or {@link #from}; every setter returns a new
 * builder. Part selections are resolved against the global catalog.
 */
public final class MaterialBuilder extends Builder<MMDMaterial, MaterialBuilder> {
  /** The empty builder every chain starts from. */
  public static final MaterialBuilder INSTANCE = new MaterialBuilder(Map.of());

  private enum Key {
    NAME,
    PARENT,
    PROPERTIES,
    SELECTION,
    EVENTS,
    RUNTIME
  }

  private final Map<Key, Object> values;

  private MaterialBuilder(Map<Key, Object> values) {
    this.values = Map.copyOf(values);
  }

  /** The empty builder: default stats, every part, a fluid. */
  public static MaterialBuilder create() {
    return INSTANCE;
  }

  @SuppressWarnings("unchecked")
  private <T> T value(Key key, T fallback) {
    return (T) values.getOrDefault(key, fallback);
  }

  private MaterialBuilder with(Key key, Object value) {
    var copy = new EnumMap<Key, Object>(Key.class);
    copy.putAll(values);
    copy.put(key, Objects.requireNonNull(value));
    return new MaterialBuilder(copy);
  }

  @Override
  public MaterialBuilder setName(ResourceLocation name) {
    return with(Key.NAME, name);
  }

  @Override
  public MaterialBuilder setName(String name) {
    return setName(ResourceLocation.parse(name));
  }

  @Override
  public ResourceLocation getRegistryName() {
    return value(Key.NAME, null);
  }

  @Override
  public MaterialBuilder setParent(ResourceLocation parent) {
    return with(Key.PARENT, parent);
  }

  /** Replaces all stats at once. */
  public MaterialBuilder setProperties(MaterialProperties properties) {
    return with(Key.PROPERTIES, properties);
  }

  /** The stats so far. */
  public MaterialProperties getProperties() {
    return value(Key.PROPERTIES, MaterialProperties.DEFAULT);
  }

  /** Replaces the whole part selection. */
  public MaterialBuilder setSelection(MaterialSelection selection) {
    return with(Key.SELECTION, selection);
  }

  /** The part selection so far. */
  public MaterialSelection getSelection() {
    return value(Key.SELECTION, MaterialSelection.currentDefaults());
  }

  /** Selects exactly {@code parts}, with every entity they need. */
  public MaterialBuilder setParts(MaterialParts parts) {
    return setSelection(MaterialSelection.fromParts(parts));
  }

  /**
   * Selects item parts with an expression such as {@code "all !scythe"}; see {@link
   * zone.moddev.mc.lib.common.types.PartSelection}.
   */
  public MaterialBuilder setItems(String expression) {
    var s = getSelection();
    var selected =
        TypeSelection.parse(
            expression,
            zone.moddev.mc.lib.api.MaterialExtensions.GLOBAL.snapshot().parts().items(),
            Set.of());
    return setSelection(
        new MaterialSelection(
            selected.standard(),
            s.blocks(),
            s.entities(),
            s.hasFluid(),
            selected.custom(),
            s.customBlocks(),
            s.fluidModule()));
  }

  /** As {@link #setItems(String)}, one token per argument. */
  public MaterialBuilder setItems(String... tokens) {
    return setItems(String.join(" ", tokens));
  }

  /** Selects block parts with an expression such as {@code "all !door"}. */
  public MaterialBuilder setBlocks(String expression) {
    var s = getSelection();
    var selected =
        TypeSelection.parse(
            expression,
            zone.moddev.mc.lib.api.MaterialExtensions.GLOBAL.snapshot().parts().blocks(),
            Set.of());
    return setSelection(
        new MaterialSelection(
            s.items(),
            selected.standard(),
            s.entities(),
            s.hasFluid(),
            s.customItems(),
            selected.custom(),
            s.fluidModule()));
  }

  /** As {@link #setBlocks(String)}, one token per argument. */
  public MaterialBuilder setBlocks(String... tokens) {
    return setBlocks(String.join(" ", tokens));
  }

  /**
   * Selects which parts' world entities (arrows, bolts) are built; a part needing an entity is
   * built only when its entity is selected.
   */
  public MaterialBuilder setEntities(String expression) {
    var s = getSelection();
    return setSelection(
        new MaterialSelection(
            s.items(),
            s.blocks(),
            PartSelection.parse(
                expression,
                zone.moddev.mc.lib.api.MaterialExtensions.GLOBAL.snapshot().parts().entities()),
            s.hasFluid(),
            s.customItems(),
            s.customBlocks(),
            s.fluidModule()));
  }

  /** As {@link #setEntities(String)}, one token per argument. */
  public MaterialBuilder setEntities(String... tokens) {
    return setEntities(String.join(" ", tokens));
  }

  /** Sets whether the material gets a fluid. */
  public MaterialBuilder setHasFluid(boolean enabled) {
    var s = getSelection();
    return setSelection(
        new MaterialSelection(
            s.items(),
            s.blocks(),
            s.entities(),
            enabled,
            s.customItems(),
            s.customBlocks(),
            s.fluidModule()));
  }

  /** Sets which fluid module builds the material's fluid ({@code mmdlib:molten} by default). */
  public MaterialBuilder setFluidModule(ResourceLocation id) {
    var s = getSelection();
    return setSelection(
        new MaterialSelection(
            s.items(), s.blocks(), s.entities(), true, s.customItems(), s.customBlocks(), id));
  }

  private MaterialBuilder edit(
      java.util.function.Function<MaterialProperties, MaterialProperties> operation) {
    return setProperties(operation.apply(getProperties()));
  }

  /** Sets the classification, such as {@code metal} or {@code gem}; not a Java class name. */
  public MaterialBuilder setType(String type) {
    return edit(
        p ->
            new MaterialProperties(
                type,
                p.color(),
                p.hardness(),
                p.strength(),
                p.magic(),
                p.damage(),
                p.harvestTags(),
                p.special()));
  }

  /** Sets the material's ARGB color, used to tint its textures. */
  public MaterialBuilder setColor(int argb) {
    return edit(
        p ->
            new MaterialProperties(
                p.type(),
                argb,
                p.hardness(),
                p.strength(),
                p.magic(),
                p.damage(),
                p.harvestTags(),
                p.special()));
  }

  /** Sets hardness, which drives mining speed, armor toughness and enchantment-related values. */
  public MaterialBuilder setHardness(double hardness) {
    return edit(
        p ->
            new MaterialProperties(
                p.type(),
                p.color(),
                hardness,
                p.strength(),
                p.magic(),
                p.damage(),
                p.harvestTags(),
                p.special()));
  }

  /** Sets strength, which drives tool and armor durability. */
  public MaterialBuilder setStrength(double strength) {
    return edit(
        p ->
            new MaterialProperties(
                p.type(),
                p.color(),
                p.hardness(),
                strength,
                p.magic(),
                p.damage(),
                p.harvestTags(),
                p.special()));
  }

  /** Sets magic affinity; enchantability is normally {@code floor(2.5 * magic)}. */
  public MaterialBuilder setMagic(double magic) {
    return edit(
        p ->
            new MaterialProperties(
                p.type(),
                p.color(),
                p.hardness(),
                p.strength(),
                magic,
                p.damage(),
                p.harvestTags(),
                p.special()));
  }

  /** Sets the tool tier's base attack-damage bonus. */
  public MaterialBuilder setDamage(double damage) {
    return edit(
        p ->
            new MaterialProperties(
                p.type(),
                p.color(),
                p.hardness(),
                p.strength(),
                p.magic(),
                damage,
                p.harvestTags(),
                p.special()));
  }

  /**
   * Sets the block tags the generator adds the material's blocks to, such as {@code
   * minecraft:needs_iron_tool}.
   */
  public MaterialBuilder setHarvestTags(Set<ResourceLocation> tags) {
    return edit(
        p ->
            new MaterialProperties(
                p.type(),
                p.color(),
                p.hardness(),
                p.strength(),
                p.magic(),
                p.damage(),
                tags,
                p.special()));
  }

  /**
   * Sets extra numeric data, including stat overrides such as {@code blast_resistance}; see {@code
   * docs/runtime-behaviors.md}.
   */
  public MaterialBuilder setSpecial(Map<String, Double> special) {
    return edit(
        p ->
            new MaterialProperties(
                p.type(),
                p.color(),
                p.hardness(),
                p.strength(),
                p.magic(),
                p.damage(),
                p.harvestTags(),
                special));
  }

  @Override
  public MaterialBuilder setEventMap(Map<String, ResourceLocation> events) {
    if (events.keySet().stream().anyMatch(String::isBlank))
      throw new IllegalArgumentException("Event name must not be blank");
    return with(Key.EVENTS, Map.copyOf(events));
  }

  /** Sets game-side settings such as sound, map color, block set type and base block. */
  public MaterialBuilder setRuntime(MaterialRuntimeSettings settings) {
    return with(Key.RUNTIME, settings);
  }

  /** A builder holding everything {@code material} has, to derive a changed copy. */
  public static MaterialBuilder from(MMDMaterial material) {
    var builder =
        create()
            .setProperties(material.properties())
            .setSelection(material.selection())
            .setEventMap(material.events())
            .setRuntime(material.runtime());
    if (material.name().isPresent()) builder = builder.setName(material.name().get());
    if (material.parent().isPresent()) builder = builder.setParent(material.parent().get());
    return builder;
  }

  @Override
  public MMDMaterial build() {
    if (getRegistryName() == null) throw new IllegalStateException("Material needs a name");
    return new MMDMaterial(
        Optional.of(getRegistryName()),
        Optional.ofNullable(value(Key.PARENT, null)),
        getProperties(),
        getSelection(),
        value(Key.EVENTS, Map.of()),
        value(Key.RUNTIME, MaterialRuntimeSettings.DEFAULT));
  }
}
