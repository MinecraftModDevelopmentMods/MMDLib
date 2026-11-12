package zone.moddev.mc.lib.machine.neoforge;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.RegisterEvent;

import zone.moddev.mc.lib.api.automation.ResourceType;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.common.loading.DefinitionLoader.SourceRoot;
import zone.moddev.mc.lib.gui.Screens;
import zone.moddev.mc.lib.gui.load.ScreenLoader;
import zone.moddev.mc.lib.machine.definition.MachineDefinition;
import zone.moddev.mc.lib.machine.definition.MachineDefinitions;
import zone.moddev.mc.lib.machine.definition.MachineScreenGenerators;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeType;

/**
 * Library-owned registration for concrete definitions in any installed mod
 * namespace.
 */
public final class MachineContent {
    public static final ResourceLocation RECOVERY = ResourceLocation
        .parse("mmdlib:machine_recovery_capsule");
    private static Map<ResourceLocation, MachineDefinition> definitions = Map
        .of();
    private static Map<ResourceLocation, Map<String, List<String>>> screenHighlights = Map
        .of();
    private static final Map<ResourceLocation, MachineRecipeType> recipes = new TreeMap<>();
    private static final Map<ResourceLocation, DefinedMachineBlock> blocks = new TreeMap<>();
    private static final Map<ResourceLocation, BlockEntityType<DefinedMachineEntity>> entities = new TreeMap<>();

    private MachineContent() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(MachineContent::registries);
        bus.addListener(MachineContent::capabilities);
        bus.addListener(MachineResourcePack::register);
        NeoForge.EVENT_BUS.addListener(GeneratedMachineRecipes::register);
    }

    public static Map<ResourceLocation, MachineDefinition> definitions() {
        return definitions;
    }

    static Map<String, List<String>> screenHighlights(ResourceLocation id) {
        return Objects.requireNonNull(screenHighlights.get(id),
            "No compiled screen for " + id);
    }

    public static MachineRecipeType recipeType(ResourceLocation id) {
        var type = recipes.get(id);
        if (type == null)
            throw new IllegalArgumentException(
                "Unknown machine recipe type " + id);
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
                    throw new IllegalArgumentException(
                        "Conflicting recipe declarations " + type.id());
            }
        }
        declarations
            .forEach((id, type) -> recipes.put(id, new MachineRecipeType(id)));
        for (var machine : definitions.values())
            if (!recipes.containsKey(machine.recipeType().id()))
                throw new IllegalArgumentException(machine.id()
                    + ": recipe type must be declared in machine XML: "
                    + machine.recipeType().id());
        GeneratedMachineRecipes.configure(declarations);
        var embedded = new TreeMap<ResourceLocation, zone.moddev.mc.lib.gui.load.ScreenReader.Node>();
        definitions
            .forEach((id, machine) -> embedded.put(id, machine.screen()));
        var screens = ScreenLoader.load(roots, embedded,
            (id, node) -> MachineScreenGenerators.generate(definitions.get(id),
                node));
        var highlights = new TreeMap<ResourceLocation, Map<String, List<String>>>();
        definitions
            .forEach((id, machine) -> highlights.put(id, MachineScreenGenerators
                .compileHighlights(machine, screens.get(id).screen())));
        screenHighlights = Map.copyOf(highlights);
        Screens.install(screens);
    }

    private static boolean same(MachineDefinition.RecipeType a,
        MachineDefinition.RecipeType b) {
        if (!a.name().equals(b.name()))
            return false;
        if (a.generator() == null || b.generator() == null)
            return a.generator() == b.generator();
        return a.generator().className().equals(b.generator().className())
            && a.generator().properties().equals(b.generator().properties())
            && a.generator().loader() == b.generator().loader();
    }

    private static void registries(RegisterEvent event) {
        event.register(Registries.RECIPE_TYPE, helper -> helper
            .register(MachineRecipeBundle.ID, MachineRecipeBundle.TYPE));
        event.register(Registries.RECIPE_SERIALIZER, helper -> helper
            .register(MachineRecipeBundle.ID, MachineRecipeBundle.SERIALIZER));
        event.register(Registries.RECIPE_TYPE,
            helper -> recipes.forEach((id, type) -> helper.register(id, type)));
        event.register(Registries.RECIPE_SERIALIZER, helper -> recipes
            .forEach((id, type) -> helper.register(id, type.serializer())));
        event.register(Registries.BLOCK,
            helper -> definitions.forEach((id, definition) -> {
                var properties = BlockBehaviour.Properties.of()
                    .strength(definition.hardness());
                if (definition.requiresTool())
                    properties.requiresCorrectToolForDrops();
                var block = new DefinedMachineBlock(properties,
                    () -> entities.get(id));
                helper.register(id, block);
                blocks.put(id, block);
            }));
        event.register(Registries.ITEM, helper -> {
            helper.register(RECOVERY, new MachineRecoveryItem());
            blocks.forEach((id, block) -> helper.register(id,
                new BlockItem(block, new Item.Properties())));
        });
        event.register(Registries.BLOCK_ENTITY_TYPE,
            helper -> definitions.forEach((id, definition) -> {
                var type = BlockEntityType.Builder.of(
                    (pos, state) -> new DefinedMachineEntity(entities.get(id),
                        pos, state, definition,
                        recipeType(definition.recipeType().id())),
                    blocks.get(id)).build(null);
                helper.register(id, type);
                entities.put(id, type);
            }));
        event.register(Registries.CREATIVE_MODE_TAB, helper -> {
            if (!definitions.isEmpty())
                helper.register(ResourceLocation.parse("mmdlib:data_machines"),
                    CreativeModeTab.builder()
                        .title(Component.literal("Data-defined Machines"))
                        .icon(() -> new ItemStack(
                            blocks.values().iterator().next()))
                        .displayItems((parameters, output) -> {
                            blocks.values().forEach(output::accept);
                            output.accept(recoveryItem());
                        }).build());
        });
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        // Capabilities register after fluids, including fluids supplied by
        // other installed mods.
        for (var definition : definitions.values())
            if (definition.fluids() != null)
                for (var tank : definition.fluids().tanks())
                    if (tank.fluid() != null
                        && (!BuiltInRegistries.FLUID.containsKey(tank.fluid())
                            || BuiltInRegistries.FLUID
                                .get(tank.fluid()) == Fluids.EMPTY))
                        throw new IllegalArgumentException(
                            definition.id() + ": unknown or empty fluid "
                                + tank.fluid() + " in tank " + tank.id());
        for (var type : entities.values()) {
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type,
                (entity,
                    side) -> entity.items.view(index -> entity.resources
                        .automation().mode(ResourceType.ITEMS,
                            entity.items.address(index).inventory(),
                            entity.items.address(index).slot(), side)));
            event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type,
                (entity, side) -> entity.fluids.getTanks() == 0
                    ? null
                    : entity.fluids.view(index -> entity.resources.automation()
                        .mode(ResourceType.FLUIDS, index, side)));
            event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type,
                (entity, side) -> entity.energy == null
                    ? null
                    : entity.energy.view(() -> side == null
                        ? IoMode.INPUT
                        : entity.resources.automation()
                            .mode(ResourceType.ENERGY, 0, side)));
        }
    }
}
