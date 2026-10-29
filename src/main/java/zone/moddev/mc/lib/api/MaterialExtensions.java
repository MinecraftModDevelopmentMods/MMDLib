package zone.moddev.mc.lib.api;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import zone.moddev.mc.lib.common.modules.ModuleCatalog;
import zone.moddev.mc.lib.common.types.*;

/**
 * The catalog of everything material definitions can refer to: item, block and fluid modules, the
 * Java implementations behind them, code-registered part types, and events.
 *
 * <p>{@link #GLOBAL} is the shared catalog. Register extensions during mod construction, before any
 * material JSON that names them is decoded. Separate instances can be created for isolated loading
 * and tests. Every registration rejects duplicate IDs.
 */
public final class MaterialExtensions {
  /** The shared catalog, with MMDLib's bundled modules and built-in implementations installed. */
  public static final MaterialExtensions GLOBAL = new MaterialExtensions();

  /**
   * A registered item part: the factory that builds its template and item, and the mod it requires,
   * if any.
   */
  public record ItemType(ItemTypeFactory<?> factory, Optional<String> requiredMod) {}

  /**
   * A registered block part: the factory that builds its template and block, and the mod it
   * requires, if any.
   */
  public record BlockType(BlockTypeFactory<?> factory, Optional<String> requiredMod) {}

  /**
   * An immutable copy of the registered item and block parts at one moment. Decoding takes a
   * snapshot, so a definition decoded earlier is not changed by later registrations.
   */
  public record Snapshot(
      Map<ResourceLocation, ItemType> items, Map<ResourceLocation, BlockType> blocks) {
    public Snapshot {
      items = Map.copyOf(items);
      blocks = Map.copyOf(blocks);
    }

    /** Every registered part, resolvable by ID or alias. */
    public PartRegistry parts() {
      var parts = new java.util.ArrayList<MaterialPart>();
      items.forEach(
          (id, type) ->
              parts.add(
                  type.factory()
                          instanceof
                          zone.moddev.mc.lib.common.modules.ModuleFactories.Reference module
                      ? module.part()
                      : new MaterialPart(
                          id, MaterialPart.Kind.ITEM, type.requiredMod(), PartMetadata.EMPTY)));
      blocks.forEach(
          (id, type) ->
              parts.add(
                  type.factory()
                          instanceof
                          zone.moddev.mc.lib.common.modules.ModuleFactories.Reference module
                      ? module.part()
                      : new MaterialPart(
                          id, MaterialPart.Kind.BLOCK, type.requiredMod(), PartMetadata.EMPTY)));
      return new PartRegistry(parts);
    }
  }

  private final ConcurrentHashMap<ResourceLocation, ItemType> items = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<ResourceLocation, BlockType> blocks = new ConcurrentHashMap<>();
  private final MaterialEvents events = new MaterialEvents();
  private final ConcurrentHashMap<ResourceLocation, ItemImplementation<?>> itemImplementations =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<ResourceLocation, BlockImplementation<?>> blockImplementations =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<
          ResourceLocation, zone.moddev.mc.lib.common.modules.DataModules.Block>
      blockModules = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<
          ResourceLocation, zone.moddev.mc.lib.common.modules.DataModules.Fluid>
      fluids = new ConcurrentHashMap<>();

  /** A catalog with MMDLib's bundled modules and built-in implementations installed. */
  public MaterialExtensions() {
    this(true);
  }

  /**
   * A catalog, optionally empty.
   *
   * @param includeBundledModules whether to install MMDLib's bundled modules and built-in
   *     implementations; {@code false} gives an empty catalog, mainly for tests
   */
  public MaterialExtensions(boolean includeBundledModules) {
    if (includeBundledModules) {
      ModuleCatalog.bundled().install(this);
      // MMDLib's own factories, through the same public registration calls an addon uses.
      zone.moddev.mc.lib.common.runtime.StandardImplementations.install(this);
    }
  }

  /**
   * Registers the Java code a data module's {@code factory} names for items.
   *
   * @throws IllegalArgumentException if the ID is already registered
   */
  public <I extends Item> void registerItemImplementation(
      ResourceLocation id, ItemImplementation<I> implementation) {
    Objects.requireNonNull(implementation.itemClass());
    if (itemImplementations.putIfAbsent(id, implementation) != null)
      throw new IllegalArgumentException("Duplicate item implementation: " + id);
  }

  /**
   * Registers the Java code a data module's {@code factory} names for blocks.
   *
   * @throws IllegalArgumentException if the ID is already registered
   */
  public <B extends Block> void registerBlockImplementation(
      ResourceLocation id, BlockImplementation<B> implementation) {
    Objects.requireNonNull(implementation.blockClass());
    if (blockImplementations.putIfAbsent(id, implementation) != null)
      throw new IllegalArgumentException("Duplicate block implementation: " + id);
  }

  /** The item implementation registered under {@code id}, if any. */
  public Optional<ItemImplementation<?>> findItemImplementation(ResourceLocation id) {
    return Optional.ofNullable(itemImplementations.get(id));
  }

  /** The block implementation registered under {@code id}, if any. */
  public Optional<BlockImplementation<?>> findBlockImplementation(ResourceLocation id) {
    return Optional.ofNullable(blockImplementations.get(id));
  }

  /**
   * The item implementation registered under {@code id}.
   *
   * @throws IllegalStateException if none is installed
   */
  public ItemImplementation<?> itemImplementation(ResourceLocation id) {
    var implementation = itemImplementations.get(id);
    if (implementation == null)
      throw new IllegalStateException("Runtime item implementation not installed: " + id);
    return implementation;
  }

  /**
   * The block implementation registered under {@code id}.
   *
   * @throws IllegalStateException if none is installed
   */
  public BlockImplementation<?> blockImplementation(ResourceLocation id) {
    var implementation = blockImplementations.get(id);
    if (implementation == null)
      throw new IllegalStateException("Runtime block implementation not installed: " + id);
    return implementation;
  }

  /**
   * Registers an item data module as a selectable item part.
   *
   * @throws IllegalArgumentException if the ID or one of its aliases is taken
   */
  public void registerItemModule(
      ResourceLocation id, zone.moddev.mc.lib.common.modules.DataModules.Item module) {
    registerItemModule(id, module, false);
  }

  /**
   * Registers an item data module, optionally replacing an existing item module with the same ID
   * (as a later datapack may).
   *
   * @param replace whether an existing item module with this ID may be replaced
   */
  public void registerItemModule(
      ResourceLocation id,
      zone.moddev.mc.lib.common.modules.DataModules.Item module,
      boolean replace) {
    validatePart(module.definition(id), replace);
    checkMod(module.requiredMod());
    var entry =
        new ItemType(
            new zone.moddev.mc.lib.common.modules.ModuleFactories.ItemFactory(this, id, module),
            module.requiredMod());
    if (replace) items.put(id, entry);
    else if (items.putIfAbsent(id, entry) != null)
      throw new IllegalArgumentException("Duplicate item module: " + id);
  }

  /**
   * Registers a block data module as a selectable block part.
   *
   * @throws IllegalArgumentException if the ID or one of its aliases is taken
   */
  public void registerBlockModule(
      ResourceLocation id, zone.moddev.mc.lib.common.modules.DataModules.Block module) {
    registerBlockModule(id, module, false);
  }

  /**
   * Registers a block data module, optionally replacing an existing block module with the same ID.
   *
   * @param replace whether an existing block module with this ID may be replaced
   */
  public void registerBlockModule(
      ResourceLocation id,
      zone.moddev.mc.lib.common.modules.DataModules.Block module,
      boolean replace) {
    validatePart(module.definition(id), replace);
    checkMod(module.requiredMod());
    var entry =
        new BlockType(
            new zone.moddev.mc.lib.common.modules.ModuleFactories.BlockFactory(this, id, module),
            module.requiredMod());
    if (replace) blocks.put(id, entry);
    else if (blocks.putIfAbsent(id, entry) != null)
      throw new IllegalArgumentException("Duplicate block module: " + id);
    blockModules.put(id, module);
  }

  /** A registered data block module, e.g. the one a standalone block names. */
  public zone.moddev.mc.lib.common.modules.DataModules.Block blockModule(ResourceLocation id) {
    var module = blockModules.get(id);
    if (module == null) throw new IllegalArgumentException("Unknown block module: " + id);
    return module;
  }

  /**
   * Registers a fluid data module, which a material selects with its {@code fluid} setting.
   *
   * @throws IllegalArgumentException if the ID is taken
   */
  public void registerFluidModule(
      ResourceLocation id, zone.moddev.mc.lib.common.modules.DataModules.Fluid module) {
    registerFluidModule(id, module, false);
  }

  /**
   * Registers a fluid data module, optionally replacing an existing one with the same ID.
   *
   * @param replace whether an existing fluid module with this ID may be replaced
   */
  public void registerFluidModule(
      ResourceLocation id,
      zone.moddev.mc.lib.common.modules.DataModules.Fluid module,
      boolean replace) {
    checkMod(module.requiredMod());
    if (replace) fluids.put(id, module);
    else if (fluids.putIfAbsent(id, module) != null)
      throw new IllegalArgumentException("Duplicate fluid module: " + id);
  }

  /**
   * The fluid module registered under {@code id}.
   *
   * @throws IllegalArgumentException if there is none
   */
  public zone.moddev.mc.lib.common.modules.DataModules.Fluid fluidModule(ResourceLocation id) {
    var module = fluids.get(id);
    if (module == null) throw new IllegalArgumentException("Unknown fluid module: " + id);
    return module;
  }

  /**
   * Whether {@code part} can be built: true unless it requires a mod that {@code installed} reports
   * missing.
   *
   * @param installed tests whether a mod ID is loaded
   */
  public boolean available(MaterialPart part, Predicate<String> installed) {
    var id = part.templateId();
    Optional<String> requirement;
    if (part.kind() == MaterialPart.Kind.ITEM && items.containsKey(id))
      requirement = items.get(id).requiredMod();
    else if (part.kind() != MaterialPart.Kind.ITEM && blocks.containsKey(id))
      requirement = blocks.get(id).requiredMod();
    else return part.available(installed);
    return requirement.isEmpty() || installed.test(requirement.get());
  }

  /**
   * A planner holding the templates for every part {@code material} selects and can build with the
   * installed mods, plus its fluid when it has one.
   *
   * @throws IllegalArgumentException if the material selects an unknown part or a fluid module
   *     whose mod is missing
   */
  public zone.moddev.mc.lib.common.builders.MaterialContentPlanner planner(
      MMDMaterial material, Predicate<String> installed) {
    var planner = new zone.moddev.mc.lib.common.builders.MaterialContentPlanner();
    for (var part : selection(material).parts()) {
      if (!available(part, installed)) continue;
      if (part.kind() == MaterialPart.Kind.ITEM) {
        var entry = items.get(part.templateId());
        if (entry == null) throw new IllegalArgumentException("Unknown item part: " + part);
        var template = entry.factory().template(material);
        if (!(entry.factory()
            instanceof zone.moddev.mc.lib.common.modules.ModuleFactories.Reference))
          template = template.setType(part.templateId().toString());
        planner = planner.withItemTemplate(part, template);
      } else {
        var entry = blocks.get(part.templateId());
        if (entry == null) throw new IllegalArgumentException("Unknown block part: " + part);
        var template = entry.factory().template(material);
        if (!(entry.factory()
            instanceof zone.moddev.mc.lib.common.modules.ModuleFactories.Reference))
          template = template.setType(part.templateId().toString());
        planner = planner.withBlockTemplate(part, template);
      }
      if (part.needsEntity())
        planner =
            planner.withEntityTemplate(
                part,
                zone.moddev.mc.lib.common.builders.EntityBuilder.create()
                    .setFactory(part.entityFactory().orElseThrow())
                    .setFactoryData(part.metadata().entityData()));
    }
    if (material.parts().hasFluid()) {
      var id = material.selection().fluidModule();
      var module = fluidModule(id);
      if (module.requiredMod().isPresent() && !installed.test(module.requiredMod().get()))
        throw new IllegalArgumentException(
            "Selected fluid module requires " + module.requiredMod().get());
      planner = planner.withFluidTemplate(module.builder(id, material));
    }
    return planner;
  }

  /**
   * The parts {@code material} selects, resolved against this catalog. Parts that need a world
   * entity are kept only when the material selects that entity.
   */
  public MaterialParts selection(MMDMaterial material) {
    var registry = snapshot().parts();
    var result = new java.util.HashSet<MaterialPart>();
    for (var part : material.parts().parts())
      result.add(registry.resolve(part.templateId().toString()).getOrThrow());
    for (var id : material.selection().customItems()) {
      var part = registry.resolve(id.toString()).getOrThrow();
      if (part.kind() != MaterialPart.Kind.ITEM)
        throw new IllegalArgumentException("Not an item part: " + id);
      if (!part.needsEntity() || material.selection().entities().contains(part)) result.add(part);
    }
    for (var id : material.selection().customBlocks()) {
      var part = registry.resolve(id.toString()).getOrThrow();
      if (part.kind() == MaterialPart.Kind.ITEM)
        throw new IllegalArgumentException("Not a block part: " + id);
      result.add(part);
    }
    result.removeIf(part -> part.needsEntity() && !material.selection().entities().contains(part));
    return new MaterialParts(result, material.parts().hasFluid());
  }

  private void validatePart(MaterialPart part, boolean replace) {
    var current = snapshot().parts();
    var existing = current.entries().get(part.templateId());
    if (existing != null
        && (!replace
            || (existing.kind() == MaterialPart.Kind.ITEM)
                != (part.kind() == MaterialPart.Kind.ITEM)))
      throw new IllegalArgumentException("Duplicate material part or conflicting section: " + part);
    var candidates = new java.util.ArrayList<>(current.entries().values());
    candidates.removeIf(candidate -> candidate.equals(part));
    candidates.add(part);
    new PartRegistry(candidates); // Reject aliases that shadow any canonical ID or another alias.
  }

  /** The event registry used by this catalog's definitions. */
  public MaterialEvents events() {
    return events;
  }

  /**
   * Registers a code-defined item part.
   *
   * @throws IllegalArgumentException if the ID or an alias is taken
   */
  public <I extends Item> void registerItemType(ResourceLocation id, ItemTypeFactory<I> factory) {
    registerItemType(id, factory, Optional.empty());
  }

  /**
   * Registers a code-defined item part that is planned only when {@code requiredMod} is loaded. Do
   * not load classes from that mod while registering.
   */
  public <I extends Item> void registerItemType(
      ResourceLocation id, ItemTypeFactory<I> factory, Optional<String> requiredMod) {
    validatePart(
        new MaterialPart(id, MaterialPart.Kind.ITEM, requiredMod, PartMetadata.EMPTY), false);
    Objects.requireNonNull(factory.itemClass());
    checkMod(requiredMod);
    if (items.putIfAbsent(id, new ItemType(factory, requiredMod)) != null)
      throw new IllegalArgumentException("Item type already registered: " + id);
  }

  /**
   * Registers a code-defined block part.
   *
   * @throws IllegalArgumentException if the ID or an alias is taken
   */
  public <B extends Block> void registerBlockType(
      ResourceLocation id, BlockTypeFactory<B> factory) {
    registerBlockType(id, factory, Optional.empty());
  }

  /**
   * Registers a code-defined block part that is planned only when {@code requiredMod} is loaded. Do
   * not load classes from that mod while registering.
   */
  public <B extends Block> void registerBlockType(
      ResourceLocation id, BlockTypeFactory<B> factory, Optional<String> requiredMod) {
    validatePart(
        new MaterialPart(id, MaterialPart.Kind.BLOCK, requiredMod, PartMetadata.EMPTY), false);
    Objects.requireNonNull(factory.blockClass());
    checkMod(requiredMod);
    if (blocks.putIfAbsent(id, new BlockType(factory, requiredMod)) != null)
      throw new IllegalArgumentException("Block type already registered: " + id);
  }

  private static void checkMod(Optional<String> mod) {
    Objects.requireNonNull(mod);
    if (mod.isPresent() && !mod.get().matches("[a-z][a-z0-9_]{1,63}"))
      throw new IllegalArgumentException("Invalid required mod ID");
  }

  /** An immutable copy of the registered parts. */
  public Snapshot snapshot() {
    return new Snapshot(Map.copyOf(items), Map.copyOf(blocks));
  }

  /**
   * Adds the extra-definition views ({@code extraItems}, {@code extraBlocks}) for code-registered
   * and addon parts to an existing plan, keyed by part ID. Kept for compatibility: plans are
   * already complete, so nothing new is planned here.
   *
   * @throws IllegalArgumentException if two planned items or blocks share an ID
   */
  public MaterialContentPlan extend(
      MMDMaterial material, MaterialContentPlan base, Predicate<String> installed) {
    var itemDefinitions = new java.util.LinkedHashMap<ResourceLocation, MMDItem>();
    var blockDefinitions = new java.util.LinkedHashMap<ResourceLocation, MMDBlock>();
    var itemIds = new java.util.HashSet<ResourceLocation>();
    if (base.fluid() != null && base.fluid().bucket() != null)
      itemIds.add(base.fluid().bucket().name());
    base.items()
        .forEach(
            (part, item) -> {
              if (!itemIds.add(item.name()))
                throw new IllegalArgumentException("Duplicate item ID: " + item.name());
              if (!ModuleCatalog.bundled().parts().entries().containsKey(part.templateId()))
                itemDefinitions.put(part.templateId(), item);
            });
    var blockIds = new java.util.HashSet<ResourceLocation>();
    if (base.fluid() != null && base.fluid().block() != null)
      blockIds.add(base.fluid().block().name());
    base.blocks()
        .forEach(
            (part, block) -> {
              if (!blockIds.add(block.name()))
                throw new IllegalArgumentException("Duplicate block ID: " + block.name());
              if (!ModuleCatalog.bundled().parts().entries().containsKey(part.templateId()))
                blockDefinitions.put(part.templateId(), block);
            });
    return new MaterialContentPlan(
        base.material(),
        base.items(),
        base.blocks(),
        base.entities(),
        base.fluid(),
        itemDefinitions,
        blockDefinitions,
        base.blockEntities());
  }

  @SuppressWarnings("unchecked")
  private <I extends Item> ItemTypeFactory<I> itemFactory(ResourceLocation id, Class<I> expected) {
    var entry = items.get(id);
    if (entry == null || entry.factory().itemClass() != expected)
      throw new IllegalArgumentException("Unknown item type or wrong class: " + id);
    return (ItemTypeFactory<I>) entry.factory();
  }

  @SuppressWarnings("unchecked")
  private <B extends Block> BlockTypeFactory<B> blockFactory(
      ResourceLocation id, Class<B> expected) {
    var entry = blocks.get(id);
    if (entry == null || entry.factory().blockClass() != expected)
      throw new IllegalArgumentException("Unknown block type or wrong class: " + id);
    return (BlockTypeFactory<B>) entry.factory();
  }

  /**
   * Queues a code-registered item type's item for one material on the mod's own register. Creation
   * is deferred until the registry supplier runs.
   *
   * @param registry a register in the material's namespace, attached to the mod bus
   * @param type the item type ID
   * @param expected the item type's class
   * @throws IllegalArgumentException if the type, class, namespace, material or definition do not
   *     match
   */
  public <I extends Item> DeferredHolder<Item, I> queueItem(
      DeferredRegister<Item> registry,
      ResourceLocation type,
      Class<I> expected,
      MMDMaterial material,
      MMDItem definition) {
    var factory = itemFactory(type, expected);
    checkDefinition(
        registry.getNamespace(),
        material,
        definition.name(),
        definition.material(),
        definition.type(),
        factory instanceof zone.moddev.mc.lib.common.modules.ModuleFactories.Reference module
            ? module.implementation()
            : type);
    return registry.register(
        definition.name().getPath(),
        () -> expected.cast(Objects.requireNonNull(factory.create(material, definition))));
  }

  /**
   * Queues a code-registered block type's block for one material on the mod's own register.
   * Creation is deferred until the registry supplier runs. The block's item is not registered here.
   *
   * @param registry a register in the material's namespace, attached to the mod bus
   * @param type the block type ID
   * @param expected the block type's class
   * @throws IllegalArgumentException if the type, class, namespace, material or definition do not
   *     match
   */
  public <B extends Block> DeferredHolder<Block, B> queueBlock(
      DeferredRegister<Block> registry,
      ResourceLocation type,
      Class<B> expected,
      MMDMaterial material,
      MMDBlock definition) {
    var factory = blockFactory(type, expected);
    checkDefinition(
        registry.getNamespace(),
        material,
        definition.name(),
        definition.material(),
        definition.type(),
        factory instanceof zone.moddev.mc.lib.common.modules.ModuleFactories.Reference module
            ? module.implementation()
            : type);
    return registry.register(
        definition.name().getPath(),
        () -> expected.cast(Objects.requireNonNull(factory.create(material, definition))));
  }

  private static void checkDefinition(
      String namespace,
      MMDMaterial material,
      ResourceLocation name,
      ResourceLocation owner,
      String declaredType,
      ResourceLocation type) {
    if (!name.getNamespace().equals(namespace)
        || !material.name().orElseThrow().equals(owner)
        || !declaredType.equals(type.toString()))
      throw new IllegalArgumentException(
          "Definition does not belong to this registry, material, or type: " + name);
  }
}
