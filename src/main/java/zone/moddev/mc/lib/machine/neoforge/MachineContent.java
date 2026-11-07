package zone.moddev.mc.lib.machine.neoforge;

import java.util.*;

import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.RegisterEvent;

import zone.moddev.mc.lib.api.automation.ResourceType;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.common.loading.DefinitionLoader.SourceRoot;
import zone.moddev.mc.lib.gui.Screens;
import zone.moddev.mc.lib.gui.load.ScreenLoader;
import zone.moddev.mc.lib.machine.definition.*;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeType;

/** Library-owned registration for concrete definitions in any installed mod namespace. */
public final class MachineContent {
  public static final ResourceLocation RECOVERY =
      ResourceLocation.parse("mmdlib:machine_recovery_capsule");
  private static Map<ResourceLocation, MachineDefinition> definitions = Map.of();
  private static final Map<ResourceLocation, MachineRecipeType> recipes = new TreeMap<>();
  private static final Map<ResourceLocation, DefinedMachineBlock> blocks = new TreeMap<>();
  private static final Map<ResourceLocation, BlockEntityType<DefinedMachineEntity>> entities =
      new TreeMap<>();

  private MachineContent() {}

  public static void register(IEventBus bus) {
    bus.addListener(MachineContent::registries);
    bus.addListener(MachineContent::capabilities);
    bus.addListener(MachineResourcePack::register);
    NeoForge.EVENT_BUS.addListener(GeneratedMachineRecipes::register);
  }

  public static Map<ResourceLocation, MachineDefinition> definitions() {
    return definitions;
  }

  public static MachineRecipeType recipeType(ResourceLocation id) {
    var type = recipes.get(id);
    if (type == null) throw new IllegalArgumentException("Unknown machine recipe type " + id);
    return type;
  }

  public static Item recoveryItem() {
    return BuiltInRegistries.ITEM.get(RECOVERY);
  }

  public static void load(List<SourceRoot> roots) {
    definitions = MachineDefinitions.load(roots);
    var declarations = new TreeMap<ResourceLocation, MachineDefinition.RecipeType>();
    for (var machine : definitions.values()) {
      var type = machine.recipeType();
      if (type.declare()) {
        var previous = declarations.putIfAbsent(type.id(), type);
        if (previous != null && !same(previous, type))
          throw new IllegalArgumentException("Conflicting recipe declarations " + type.id());
      }
    }
    declarations.forEach((id, type) -> recipes.put(id, new MachineRecipeType(id)));
    for (var machine : definitions.values())
      if (!recipes.containsKey(machine.recipeType().id()))
        throw new IllegalArgumentException(
            machine.id()
                + ": recipe type must be declared in machine XML: "
                + machine.recipeType().id());
    GeneratedMachineRecipes.configure(declarations);
    var embedded = new TreeMap<ResourceLocation, zone.moddev.mc.lib.gui.load.ScreenReader.Node>();
    definitions.forEach((id, machine) -> embedded.put(id, machine.screen()));
    var screens =
        ScreenLoader.load(
            roots,
            embedded,
            (id, node) -> MachineScreenGenerators.generate(definitions.get(id), node));
    definitions.forEach(
        (id, machine) -> MachineScreenGenerators.validate(machine, screens.get(id).screen()));
    Screens.install(screens);
  }

  private static boolean same(MachineDefinition.RecipeType a, MachineDefinition.RecipeType b) {
    if (!a.name().equals(b.name())) return false;
    if (a.generator() == null || b.generator() == null) return a.generator() == b.generator();
    return a.generator().className().equals(b.generator().className())
        && a.generator().properties().equals(b.generator().properties())
        && a.generator().loader() == b.generator().loader();
  }

  private static void registries(RegisterEvent event) {
    event.register(
        Registries.RECIPE_TYPE,
        helper -> helper.register(MachineRecipeBundle.ID, MachineRecipeBundle.TYPE));
    event.register(
        Registries.RECIPE_SERIALIZER,
        helper -> helper.register(MachineRecipeBundle.ID, MachineRecipeBundle.SERIALIZER));
    event.register(
        Registries.RECIPE_TYPE, helper -> recipes.forEach((id, type) -> helper.register(id, type)));
    event.register(
        Registries.RECIPE_SERIALIZER,
        helper -> recipes.forEach((id, type) -> helper.register(id, type.serializer())));
    event.register(
        Registries.BLOCK,
        helper ->
            definitions.forEach(
                (id, definition) -> {
                  var properties = BlockBehaviour.Properties.of().strength(definition.hardness());
                  if (definition.requiresTool()) properties.requiresCorrectToolForDrops();
                  var block = new DefinedMachineBlock(properties, () -> entities.get(id));
                  helper.register(id, block);
                  blocks.put(id, block);
                }));
    event.register(
        Registries.ITEM,
        helper -> {
          helper.register(RECOVERY, new MachineRecoveryItem());
          blocks.forEach(
              (id, block) -> helper.register(id, new BlockItem(block, new Item.Properties())));
        });
    event.register(
        Registries.BLOCK_ENTITY_TYPE,
        helper ->
            definitions.forEach(
                (id, definition) -> {
                  var type =
                      BlockEntityType.Builder.of(
                              (pos, state) ->
                                  new DefinedMachineEntity(
                                      entities.get(id),
                                      pos,
                                      state,
                                      definition,
                                      recipeType(definition.recipeType().id())),
                              blocks.get(id))
                          .build(null);
                  helper.register(id, type);
                  entities.put(id, type);
                }));
    event.register(
        Registries.CREATIVE_MODE_TAB,
        helper -> {
          if (!definitions.isEmpty())
            helper.register(
                ResourceLocation.parse("mmdlib:data_machines"),
                CreativeModeTab.builder()
                    .title(Component.literal("Data-defined Machines"))
                    .icon(() -> new ItemStack(blocks.values().iterator().next()))
                    .displayItems(
                        (parameters, output) -> {
                          blocks.values().forEach(output::accept);
                          output.accept(recoveryItem());
                        })
                    .build());
        });
  }

  private static void capabilities(RegisterCapabilitiesEvent event) {
    for (var type : entities.values()) {
      event.registerBlockEntity(
          Capabilities.ItemHandler.BLOCK,
          type,
          (entity, side) ->
              entity.items.view(
                  index -> entity.resources.automation().mode(ResourceType.ITEMS, index, side)));
      event.registerBlockEntity(
          Capabilities.EnergyStorage.BLOCK,
          type,
          (entity, side) ->
              entity.energy.view(
                  () ->
                      side == null
                          ? IoMode.INPUT
                          : entity.resources.automation().mode(ResourceType.ENERGY, 0, side)));
    }
  }
}
