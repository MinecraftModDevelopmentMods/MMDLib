package zone.moddev.mc.lib.common.builders;

import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import net.minecraft.resources.ResourceLocation;

import net.neoforged.fml.ModList;

import zone.moddev.mc.lib.common.modules.ModuleCatalog;
import zone.moddev.mc.lib.common.types.*;

/**
 * Turns a material's selected parts into a {@link
 * zone.moddev.mc.lib.common.types.MaterialContentPlan}, by binding each part's template to the
 * material. Starts with the bundled modules' templates; the {@code with...} methods return a new
 * planner with a template added or replaced.
 */
public final class MaterialContentPlanner {
  private final Map<MaterialPart, ItemBuilder> items;
  private final Map<MaterialPart, BlockBuilder> blocks;
  private final Map<MaterialPart, EntityBuilder> entities;
  private final FluidBuilder fluid;

  /** A planner with the templates of every bundled module and the first bundled fluid module. */
  public MaterialContentPlanner() {
    var items = new java.util.HashMap<MaterialPart, ItemBuilder>();
    var blocks = new java.util.HashMap<MaterialPart, BlockBuilder>();
    var entities = new java.util.HashMap<MaterialPart, EntityBuilder>();
    var modules = ModuleCatalog.bundled();
    modules
        .items()
        .forEach(
            (id, module) ->
                items.put(
                    module.definition(id),
                    module.template().toBuilder().setType(module.factory().toString())));
    modules
        .blocks()
        .forEach(
            (id, module) ->
                blocks.put(
                    module.definition(id),
                    module.template().toBuilder().setType(module.factory().toString())));
    for (var part : modules.parts().entities())
      entities.put(
          part,
          EntityBuilder.create()
              .setFactory(part.entityFactory().orElseThrow())
              .setFactoryData(part.metadata().entityData()));
    this.items = Map.copyOf(items);
    this.blocks = Map.copyOf(blocks);
    this.entities = Map.copyOf(entities);
    var fluidEntry = modules.fluids().entrySet().iterator().next();
    this.fluid =
        fluidEntry.getValue().template().toBuilder()
            .setName(fluidEntry.getKey())
            .setFactory(fluidEntry.getValue().factory())
            .setFactoryData(fluidEntry.getValue().factoryData());
  }

  private MaterialContentPlanner(
      Map<MaterialPart, ItemBuilder> items,
      Map<MaterialPart, BlockBuilder> blocks,
      Map<MaterialPart, EntityBuilder> entities,
      FluidBuilder fluid) {
    this.items = Map.copyOf(items);
    this.blocks = Map.copyOf(blocks);
    this.entities = Map.copyOf(entities);
    this.fluid = Objects.requireNonNull(fluid);
  }

  /**
   * A planner that uses {@code template} for item part {@code part}.
   *
   * @throws IllegalArgumentException if {@code part} is a block part
   */
  public MaterialContentPlanner withItemTemplate(MaterialPart part, ItemBuilder template) {
    if (part.kind() != MaterialPart.Kind.ITEM)
      throw new IllegalArgumentException("Use the block template for " + part);
    var copy = new java.util.HashMap<MaterialPart, ItemBuilder>();
    copy.putAll(items);
    copy.put(part, Objects.requireNonNull(template));
    return new MaterialContentPlanner(copy, blocks, entities, fluid);
  }

  /**
   * A planner that uses {@code template} for block part {@code part}.
   *
   * @throws IllegalArgumentException if {@code part} is an item part
   */
  public MaterialContentPlanner withBlockTemplate(MaterialPart part, BlockBuilder template) {
    if (part.kind() == MaterialPart.Kind.ITEM)
      throw new IllegalArgumentException("Not a block part: " + part);
    var copy = new java.util.HashMap<MaterialPart, BlockBuilder>();
    copy.putAll(blocks);
    copy.put(part, Objects.requireNonNull(template));
    return new MaterialContentPlanner(items, copy, entities, fluid);
  }

  /**
   * A planner that uses {@code template} for the world entity of {@code part}.
   *
   * @throws IllegalArgumentException if {@code part} needs no entity
   */
  public MaterialContentPlanner withEntityTemplate(MaterialPart part, EntityBuilder template) {
    if (!part.needsEntity())
      throw new IllegalArgumentException("No standard entity companion for " + part);
    var copy = new java.util.HashMap<MaterialPart, EntityBuilder>();
    copy.putAll(entities);
    copy.put(part, Objects.requireNonNull(template));
    return new MaterialContentPlanner(items, blocks, copy, fluid);
  }

  /** A planner that uses {@code template} for the material's fluid. */
  public MaterialContentPlanner withFluidTemplate(FluidBuilder template) {
    return new MaterialContentPlanner(items, blocks, entities, template);
  }

  /**
   * Plans {@code selection} for {@code material}, skipping parts whose required mod is not loaded.
   * Needs NeoForge running.
   */
  public MaterialContentPlan plan(ResourceLocation material, MaterialParts selection) {
    return plan(material, selection, modId -> ModList.get().isLoaded(modId));
  }

  /**
   * Plans {@code selection} for {@code material}, skipping parts whose required mod {@code
   * installed} reports missing. Usable without NeoForge, e.g. in tests.
   */
  public MaterialContentPlan plan(
      ResourceLocation material, MaterialParts selection, Predicate<String> installed) {
    return plan(material, selection, installed, part -> part.available(installed));
  }

  /**
   * Plans {@code selection} for {@code material}, building only the parts {@code available}
   * accepts.
   *
   * @param installed tests whether a mod is loaded
   * @param available decides per part whether to build it
   * @throws IllegalArgumentException if a selected part has no template
   */
  public MaterialContentPlan plan(
      ResourceLocation material,
      MaterialParts selection,
      Predicate<String> installed,
      Predicate<MaterialPart> available) {
    Objects.requireNonNull(material);
    Objects.requireNonNull(selection);
    Objects.requireNonNull(installed);
    var itemDefinitions = new java.util.HashMap<MaterialPart, MMDItem>();
    var blockDefinitions = new java.util.HashMap<MaterialPart, MMDBlock>();
    var entityDefinitions = new java.util.HashMap<MaterialPart, MMDEntity>();
    var blockEntities = new java.util.HashMap<MaterialPart, MMDBlockEntity>();
    for (MaterialPart part : selection.parts().stream().sorted().toList()) {
      if (!available.test(part)) continue;
      if (part.kind() == MaterialPart.Kind.ITEM) {
        itemDefinitions.put(
            part,
            requireTemplate(items, part).setName(part.templateId()).forMaterial(material).build());
      } else {
        var block =
            requireTemplate(blocks, part).setName(part.templateId()).forMaterial(material).build();
        blockDefinitions.put(part, block);
        part.metadata()
            .blockEntity()
            .ifPresent(template -> blockEntities.put(part, template.bind(block)));
        if (block.item() != null) itemDefinitions.put(part, block.item());
      }
      if (part.needsEntity())
        entityDefinitions.put(
            part,
            requireTemplate(entities, part)
                .setName(part.templateId())
                .forMaterial(material)
                .build());
    }
    var fluidDefinition = selection.hasFluid() ? fluid.forMaterial(material).build() : null;
    return new MaterialContentPlan(
        material,
        itemDefinitions,
        blockDefinitions,
        entityDefinitions,
        fluidDefinition,
        Map.of(),
        Map.of(),
        blockEntities);
  }

  private static <T> T requireTemplate(Map<MaterialPart, T> templates, MaterialPart part) {
    var template = templates.get(part);
    if (template == null)
      throw new IllegalArgumentException("No template configured for material part: " + part);
    return template;
  }
}
