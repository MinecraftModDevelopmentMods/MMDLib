package zone.moddev.mc.lib.common.types;

import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import zone.moddev.mc.lib.api.MaterialExtensions;

/**
 * What a material selects, from its {@code items}, {@code blocks}, {@code entities}, {@code
 * has_fluid} and {@code fluid} fields. Each section is a selection expression such as {@code "all
 * !scythe"}; see {@link PartSelection}.
 *
 * @param items the item parts
 * @param blocks the block parts
 * @param entities the parts whose world entities are wanted (e.g. arrows); a part that needs an
 *     entity is built only when listed here
 * @param hasFluid whether the material gets a fluid
 * @param customItems code-registered item types selected, by ID
 * @param customBlocks code-registered block types selected, by ID
 * @param fluidModule the fluid module used, {@code mmdlib:molten} by default
 */
public record MaterialSelection(
    Set<MaterialPart> items,
    Set<MaterialPart> blocks,
    Set<MaterialPart> entities,
    boolean hasFluid,
    Set<net.minecraft.resources.ResourceLocation> customItems,
    Set<net.minecraft.resources.ResourceLocation> customBlocks,
    net.minecraft.resources.ResourceLocation fluidModule) {
  /** Every bundled part, entity and the default fluid. */
  public static final MaterialSelection DEFAULT =
      new MaterialSelection(
          PartSelection.ITEMS, PartSelection.BLOCKS, PartSelection.ENTITIES, true);

  /** A selection with no code-registered types and the default fluid module. */
  public MaterialSelection(
      Set<MaterialPart> items,
      Set<MaterialPart> blocks,
      Set<MaterialPart> entities,
      boolean hasFluid) {
    this(items, blocks, entities, hasFluid, Set.of(), Set.of());
  }

  /** A selection with the default fluid module. */
  public MaterialSelection(
      Set<MaterialPart> items,
      Set<MaterialPart> blocks,
      Set<MaterialPart> entities,
      boolean hasFluid,
      Set<net.minecraft.resources.ResourceLocation> customItems,
      Set<net.minecraft.resources.ResourceLocation> customBlocks) {
    this(
        items,
        blocks,
        entities,
        hasFluid,
        customItems,
        customBlocks,
        net.minecraft.resources.ResourceLocation.parse("mmdlib:molten"));
  }

  /**
   * Copies every set and checks that each part is in the section for its kind.
   *
   * @throws IllegalArgumentException if a block part is in {@code items}, an item part in {@code
   *     blocks}, or a part without an entity in {@code entities}
   */
  public MaterialSelection {
    items = Set.copyOf(items);
    blocks = Set.copyOf(blocks);
    entities = Set.copyOf(entities);
    java.util.Objects.requireNonNull(fluidModule);
    customItems = Set.copyOf(customItems);
    customBlocks = Set.copyOf(customBlocks);
    if (items.stream().anyMatch(p -> p.kind() != MaterialPart.Kind.ITEM)
        || blocks.stream().anyMatch(p -> p.kind() == MaterialPart.Kind.ITEM)
        || entities.stream().anyMatch(p -> !p.needsEntity()))
      throw new IllegalArgumentException("Part is in the wrong section");
  }

  /** Every part in {@code snapshot}, with a fluid: what a material gets when it selects nothing. */
  public static MaterialSelection defaults(MaterialExtensions.Snapshot snapshot) {
    var registry = snapshot.parts();
    return new MaterialSelection(registry.items(), registry.blocks(), registry.entities(), true);
  }

  /** {@link #defaults} for the global catalog as it is now. */
  public static MaterialSelection currentDefaults() {
    return defaults(MaterialExtensions.GLOBAL.snapshot());
  }

  /**
   * The selection fields, resolved against the global catalog when this class loads; prefer {@link
   * #mapCodec} with a current snapshot.
   */
  public static final MapCodec<MaterialSelection> MAP_CODEC =
      mapCodec(MaterialExtensions.GLOBAL.snapshot());

  /**
   * The selection fields of a material file, resolved against {@code snapshot}. Omitted sections
   * select everything.
   */
  public static MapCodec<MaterialSelection> mapCodec(MaterialExtensions.Snapshot snapshot) {
    var registry = snapshot.parts();
    return RecordCodecBuilder.mapCodec(
        i ->
            i.group(
                    PartSelection.codec(registry.items())
                        .optionalFieldOf("items", registry.items())
                        .forGetter(MaterialSelection::items),
                    PartSelection.codec(registry.blocks())
                        .optionalFieldOf("blocks", registry.blocks())
                        .forGetter(MaterialSelection::blocks),
                    PartSelection.codec(registry.entities())
                        .optionalFieldOf("entities", registry.entities())
                        .forGetter(MaterialSelection::entities),
                    Codec.BOOL
                        .optionalFieldOf("has_fluid", true)
                        .forGetter(MaterialSelection::hasFluid),
                    net.minecraft.resources.ResourceLocation.CODEC
                        .optionalFieldOf(
                            "fluid",
                            net.minecraft.resources.ResourceLocation.parse("mmdlib:molten"))
                        .forGetter(MaterialSelection::fluidModule))
                .apply(
                    i,
                    (items, blocks, entities, fluid, module) ->
                        new MaterialSelection(
                            items, blocks, entities, fluid, Set.of(), Set.of(), module)));
  }

  /** The selected item and block parts, leaving out parts whose entity is not selected. */
  public MaterialParts parts() {
    var parts = new java.util.HashSet<MaterialPart>();
    parts.addAll(items);
    parts.addAll(blocks);
    parts.removeIf(part -> part.needsEntity() && !entities.contains(part));
    return new MaterialParts(parts, hasFluid);
  }

  /** The selection that produces exactly {@code selection}, with every needed entity selected. */
  public static MaterialSelection fromParts(MaterialParts selection) {
    var items = new java.util.HashSet<MaterialPart>();
    var blocks = new java.util.HashSet<MaterialPart>();
    for (var part : selection.parts())
      if (part.kind() == MaterialPart.Kind.ITEM) items.add(part);
      else blocks.add(part);
    var entities =
        selection.parts().stream()
            .filter(MaterialPart::needsEntity)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    return new MaterialSelection(items, blocks, entities, selection.hasFluid());
  }
}
