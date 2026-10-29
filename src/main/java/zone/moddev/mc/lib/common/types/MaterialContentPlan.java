package zone.moddev.mc.lib.common.types;

import java.util.Map;
import java.util.Objects;

import net.minecraft.resources.ResourceLocation;

/**
 * Every definition one material produces: what will be registered, before any Minecraft object
 * exists. Built by {@link zone.moddev.mc.lib.common.builders.MaterialContentPlanner}; the runtime
 * registrar turns it into game objects.
 *
 * @param material the material ID
 * @param items item definitions by part
 * @param blocks block definitions by part
 * @param entities world-entity definitions (e.g. arrows) by the part that needs them
 * @param fluid the fluid and its block and bucket, or null when the material has none
 * @param extraItems item definitions of code-registered and addon parts, by part ID (a
 *     compatibility view)
 * @param extraBlocks block definitions of code-registered and addon parts, by part ID (a
 *     compatibility view)
 * @param blockEntities block-entity definitions by the block part they belong to
 */
public record MaterialContentPlan(
    ResourceLocation material,
    Map<MaterialPart, MMDItem> items,
    Map<MaterialPart, MMDBlock> blocks,
    Map<MaterialPart, MMDEntity> entities,
    MMDFluid fluid,
    Map<ResourceLocation, MMDItem> extraItems,
    Map<ResourceLocation, MMDBlock> extraBlocks,
    Map<MaterialPart, MMDBlockEntity> blockEntities) {
  /** A plan without extra definitions or block entities. */
  public MaterialContentPlan(
      ResourceLocation material,
      Map<MaterialPart, MMDItem> items,
      Map<MaterialPart, MMDBlock> blocks,
      Map<MaterialPart, MMDEntity> entities,
      MMDFluid fluid) {
    this(material, items, blocks, entities, fluid, Map.of(), Map.of());
  }

  /** A plan without block entities. */
  public MaterialContentPlan(
      ResourceLocation material,
      Map<MaterialPart, MMDItem> items,
      Map<MaterialPart, MMDBlock> blocks,
      Map<MaterialPart, MMDEntity> entities,
      MMDFluid fluid,
      Map<ResourceLocation, MMDItem> extraItems,
      Map<ResourceLocation, MMDBlock> extraBlocks) {
    this(material, items, blocks, entities, fluid, extraItems, extraBlocks, Map.of());
  }

  public MaterialContentPlan {
    Objects.requireNonNull(material);
    items = Map.copyOf(items);
    blocks = Map.copyOf(blocks);
    entities = Map.copyOf(entities);
    blockEntities = Map.copyOf(blockEntities);
    for (var entry : blockEntities.entrySet()) {
      var block = blocks.get(entry.getKey());
      var entity = entry.getValue();
      if (block == null
          || !entity.block().equals(block.name())
          || !entity.material().equals(material))
        throw new IllegalArgumentException(
            "Block entity must belong to a selected block and material: " + entity.name());
    }
    extraItems = Map.copyOf(extraItems);
    extraBlocks = Map.copyOf(extraBlocks);
  }

  /**
   * Every item definition by registry ID, without duplicates: parts' items, extra items, and the
   * fluid's bucket.
   *
   * @throws IllegalArgumentException if two different definitions share an ID
   */
  public Map<ResourceLocation, MMDItem> itemDefinitions() {
    var result = definitions(items.values(), extraItems.values(), MMDItem::name);
    if (fluid != null && fluid.bucket() != null)
      putDefinition(result, fluid.bucket().name(), fluid.bucket());
    return Map.copyOf(result);
  }

  /**
   * Every block definition by registry ID, without duplicates: parts' blocks, extra blocks, and the
   * fluid's block.
   *
   * @throws IllegalArgumentException if two different definitions share an ID
   */
  public Map<ResourceLocation, MMDBlock> blockDefinitions() {
    var result = definitions(blocks.values(), extraBlocks.values(), MMDBlock::name);
    if (fluid != null && fluid.block() != null)
      putDefinition(result, fluid.block().name(), fluid.block());
    return Map.copyOf(result);
  }

  /** Every block-entity definition by registry ID. */
  public Map<ResourceLocation, MMDBlockEntity> blockEntityDefinitions() {
    return Map.copyOf(
        definitions(blockEntities.values(), java.util.List.of(), MMDBlockEntity::name));
  }

  private static <T> Map<ResourceLocation, T> definitions(
      java.util.Collection<T> primary,
      java.util.Collection<T> compatibility,
      java.util.function.Function<T, ResourceLocation> name) {
    var result = new java.util.LinkedHashMap<ResourceLocation, T>();
    primary.forEach(value -> putDefinition(result, name.apply(value), value));
    compatibility.forEach(value -> putDefinition(result, name.apply(value), value));
    return result;
  }

  private static <T> void putDefinition(
      Map<ResourceLocation, T> result, ResourceLocation name, T definition) {
    var previous = result.putIfAbsent(name, definition);
    if (previous != null && !previous.equals(definition))
      throw new IllegalArgumentException("Conflicting definitions: " + name);
  }
}
