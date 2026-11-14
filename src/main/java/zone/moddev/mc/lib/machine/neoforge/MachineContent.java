package zone.moddev.mc.lib.machine.neoforge;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
    /**
     * Items that may sneak-right-click a portable machine to preserve its
     * payload.
     */
    public static final net.minecraft.tags.TagKey<Item> PICKUP_TOOLS = net.minecraft.tags.TagKey
        .create(Registries.ITEM,
            ResourceLocation.parse("mmdlib:machine_pickup_tools"));
    public static final ResourceLocation RECOVERY = ResourceLocation
        .parse("mmdlib:machine_recovery_capsule");
    private static Map<ResourceLocation, MachineDefinition> definitions = Map
        .of();
    private static Map<ResourceLocation, Map<String, List<String>>> screenHighlights = Map
        .of();
    private static final Map<ResourceLocation, MachineRecipeType> recipes = new TreeMap<>();
    private static final Map<ResourceLocation, DefinedMachineBlock> blocks = new TreeMap<>();
    private static final Map<ResourceLocation, DefinedMachineItem> items = new TreeMap<>();
    private static final Map<ResourceLocation, BlockEntityType<DefinedMachineEntity>> entities = new TreeMap<>();

    private MachineContent() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(MachineContent::registries);
        bus.addListener(MachineContent::capabilities);
        bus.addListener(MachineResourcePack::register);
        NeoForge.EVENT_BUS.addListener(GeneratedMachineRecipes::register);
        NeoForge.EVENT_BUS.addListener(MachineItemInteractions::secondaryUse);
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

    /** The XML-registered block; available after block registration. */
    public static DefinedMachineBlock block(ResourceLocation id) {
        return Objects.requireNonNull(blocks.get(id),
            "XML machine block is not registered: " + id);
    }

    /** The XML-registered item; available after item registration. */
    public static DefinedMachineItem item(ResourceLocation id) {
        return Objects.requireNonNull(items.get(id),
            "XML machine item is not registered: " + id);
    }

    /**
     * The XML-registered entity type; available after entity type registration.
     */
    public static BlockEntityType<DefinedMachineEntity> entityType(
        ResourceLocation id) {
        return Objects.requireNonNull(entities.get(id),
            "Unknown XML machine: " + id);
    }

    public static Item recoveryItem() {
        return BuiltInRegistries.ITEM.get(RECOVERY);
    }

    /**
     * Requires each of the machine's {@code on-start} and {@code on-finish} IDs
     * to be a registered handler of its event; scripts and mods register theirs
     * before machines load.
     */
    private static void checkEvents(MachineDefinition machine) {
        var events = zone.moddev.mc.lib.api.MaterialExtensions.GLOBAL.events();
        machine.events().start()
            .ifPresent(id -> checkEvent(machine, "on-start", id,
                zone.moddev.mc.lib.api.MaterialEventTypes.MACHINE_CYCLE_START,
                events));
        machine.events().finish()
            .ifPresent(id -> checkEvent(machine, "on-finish", id,
                zone.moddev.mc.lib.api.MaterialEventTypes.MACHINE_CYCLE_FINISH,
                events));
    }

    private static void checkEvent(MachineDefinition machine, String attribute,
        ResourceLocation id, zone.moddev.mc.lib.api.MaterialEvent<?> event,
        zone.moddev.mc.lib.api.MaterialEvents events) {
        var callback = events.callbacks().get(id);
        if (callback == null)
            throw new IllegalArgumentException(machine.id() + ": " + attribute
                + " names " + id + ", which no mod or script registered");
        if (!callback.event().equals(event))
            throw new IllegalArgumentException(machine.id() + ": " + attribute
                + " names " + id + ", which is a " + callback.event().name()
                + " handler, not " + event.name());
    }

    public static void load(List<SourceRoot> roots) {
        definitions = MachineDefinitions.load(roots);
        var declarations = new TreeMap<ResourceLocation, MachineDefinition.RecipeType>();
        for (var machine : definitions.values())
            machine.processingType()
                .filter(MachineDefinition.RecipeType::declare)
                .ifPresent(type -> {
                    var previous = declarations.putIfAbsent(type.id(), type);
                    if (previous != null && !same(previous, type))
                        throw new IllegalArgumentException(
                            "Conflicting recipe declarations " + type.id());
                });
        declarations
            .forEach((id, type) -> recipes.put(id, new MachineRecipeType(id)));
        for (var machine : definitions.values())
            machine.processingType().ifPresent(type -> {
                if (!recipes.containsKey(type.id()))
                    throw new IllegalArgumentException(machine.id()
                        + ": recipe type must be declared in machine XML: "
                        + type.id());
            });
        definitions.values().forEach(MachineContent::checkEvents);
        GeneratedMachineRecipes.configure(declarations);
        var embedded = new TreeMap<ResourceLocation, zone.moddev.mc.lib.gui.load.ScreenReader.Node>();
        definitions
            .forEach((id, machine) -> embedded.put(id, machine.screen()));
        var screens = ScreenLoader.load(roots, embedded,
            MachineScreenGenerators.forMachines(definitions));
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
                if (definition.behavior().noOcclusion())
                    properties.noOcclusion();
                if (definition.behavior().immovable())
                    properties.pushReaction(
                        net.minecraft.world.level.material.PushReaction.BLOCK);
                if (!definition.behavior().shapes().isEmpty())
                    properties.dynamicShape().noOcclusion();
                var block = new DefinedMachineBlock(properties,
                    () -> entities.get(id));
                helper.register(id, block);
                blocks.put(id, block);
            }));
        event.register(Registries.ITEM, helper -> {
            helper.register(RECOVERY, new MachineRecoveryItem());
            blocks.forEach((id, block) -> {
                var item = new DefinedMachineItem(block, definitions.get(id));
                helper.register(id, item);
                items.put(id, item);
            });
        });
        event.register(Registries.BLOCK_ENTITY_TYPE,
            helper -> definitions.forEach((id, definition) -> {
                var type = BlockEntityType.Builder.of(
                    (pos, state) -> new DefinedMachineEntity(entities.get(id),
                        pos, state, definition,
                        definition.processingType()
                            .map(recipe -> recipeType(recipe.id()))
                            .orElse(null)),
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
            validateResources(definition);
        for (var type : entities.values()) {
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type,
                (entity, side) -> entity.items.getSlots() == 0
                    ? null
                    : entity.items.view(index -> entity.resources.automation()
                        .mode(ResourceType.ITEMS,
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
    static void validateResources(MachineDefinition definition) {
        for (var ghost : definition.ghosts())
            if (ghost.defaultItem() != null && (!BuiltInRegistries.ITEM
                .containsKey(ghost.defaultItem())
                || BuiltInRegistries.ITEM.get(
                    ghost.defaultItem()) == net.minecraft.world.item.Items.AIR))
                throw new IllegalArgumentException(definition.id()
                    + ": unknown or empty default item " + ghost.defaultItem()
                    + " in ghost group " + ghost.id());
        if (definition.fluids() != null)
            for (var tank : definition.fluids().tanks())
                if (tank.fluid() != null
                    && (!BuiltInRegistries.FLUID.containsKey(tank.fluid())
                        || BuiltInRegistries.FLUID
                            .get(tank.fluid()) == Fluids.EMPTY))
                    throw new IllegalArgumentException(
                        definition.id() + ": unknown or empty fluid "
                            + tank.fluid() + " in tank " + tank.id());
    }

}
