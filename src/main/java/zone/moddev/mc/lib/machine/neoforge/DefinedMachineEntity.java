package zone.moddev.mc.lib.machine.neoforge;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import zone.moddev.mc.lib.api.automation.ResourceGroup;
import zone.moddev.mc.lib.api.automation.ResourceType;
import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.api.capabilities.FluidStorage;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.api.capabilities.ItemInventories;
import zone.moddev.mc.lib.api.capabilities.SideConfig;
import zone.moddev.mc.lib.api.redstone.RedstoneControl;
import zone.moddev.mc.lib.common.types.SoundDefinition;
import zone.moddev.mc.lib.machine.definition.MachineDefinition;
import zone.moddev.mc.lib.machine.feature.RecipeProcessor;
import zone.moddev.mc.lib.machine.presentation.BuiltinVisualVariables;
import zone.moddev.mc.lib.machine.presentation.VisualVariables;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeType;

/** Shared runtime assembled from XML. There is no per-machine Java subclass. */
public final class DefinedMachineEntity extends BaseMachine
    implements zone.moddev.mc.lib.machine.feature.ProcessorMachine,
    zone.moddev.mc.lib.machine.presentation.VisualizerStyle.Provider {
    public final MachineDefinition definition;
    private Runnable placementComplete;
    private boolean placementNotified;
    private final java.util.Map<zone.moddev.mc.lib.machine.definition.AdmissionDeclaration, zone.moddev.mc.lib.machine.feature.StorageAdmission> admissionRules = new java.util.HashMap<>();
    public final ItemInventories items;
    public final FluidStorage fluids;
    public final EnergyBuffer energy;
    private final zone.moddev.mc.lib.machine.feature.ProcessorEnergy energyProvider;
    private final List<Integer> rangeSlots;
    public final MachineResources resources;
    public final MachineInventoryLifecycle inventory;

    @Override
    public zone.moddev.mc.lib.machine.inventory.MachineInventory resources() {
        return resources.recipes();
    }
    private final RedstoneControl redstone;
    private final MachineVisualState visuals;
    private final MachineShapeState shapes;
    private ResourceLocation legacyGhostRecipe;
    private final java.util.Map<String, zone.moddev.mc.lib.machine.inventory.GhostItemGroup> ghosts = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, zone.moddev.mc.lib.machine.feature.WorkTargets> workTargets = new java.util.LinkedHashMap<>();

    private MachineRecipeType recipeType(
        zone.moddev.mc.lib.machine.definition.ProcessorConfiguration config,
        MachineRecipeType primary) {
        return primary != null && primary.id().equals(config.recipeType().id())
            ? primary
            : MachineContent.recipeType(config.recipeType().id());
    }

    private void installRecipeAdmission(MachineRecipeType primary) {
        var itemRules = new java.util.HashMap<Integer, java.util.List<java.util.function.Predicate<net.minecraft.world.item.ItemStack>>>();
        var fluidRules = new java.util.HashMap<Integer, java.util.List<java.util.function.Predicate<net.neoforged.neoforge.fluids.FluidStack>>>();
        for (var config : definition.processors()) {
            if (config.recipeType() == null)
                continue;
            var filters = new RecipeInputFilters(this::getLevel,
                resources.recipes().view(config.bindings()));
            for (String alias : config.filterInputs()) {
                String target = config.bindings().getOrDefault(alias, alias);
                var links = List.of(new RecipeInputFilters.Link(
                    recipeType(config, primary), alias));
                for (var group : definition.groups())
                    if (group.id().equals(target))
                        for (int member : group.members())
                            itemRules
                                .computeIfAbsent(member,
                                    ignored -> new java.util.ArrayList<>())
                                .add(
                                    stack -> filters.acceptsItem(links, stack));
                if (definition.fluids() != null)
                    for (var group : definition.fluids().groups())
                        if (group.id().equals(target))
                            for (int member : group.members())
                                fluidRules
                                    .computeIfAbsent(member,
                                        ignored -> new java.util.ArrayList<>())
                                    .add(stack -> filters.acceptsFluid(links,
                                        stack));
            }
        }
        itemRules.forEach((slot, rules) -> items.restrict(
            stack -> rules.stream().anyMatch(rule -> rule.test(stack)), slot));
        fluidRules.forEach((tank, rules) -> fluids.restrict(
            stack -> rules.stream().anyMatch(rule -> rule.test(stack)), tank));
    }

    /** Stable capability views evaluate both policies on every call. */
    public net.neoforged.neoforge.energy.IEnergyStorage energyCapability(
        @org.jetbrains.annotations.Nullable net.minecraft.core.Direction side) {
        return energy == null ? null : energy.view(() -> {
            var configured = side == null
                ? (energyProvider == null ? IoMode.INPUT : IoMode.BOTH)
                : resources.automation().mode(ResourceType.ENERGY, 0, side);
            var supplied = energyProvider == null
                ? IoMode.BOTH
                : energyProvider.energyMode(side);
            boolean input = configured.canInsert() && supplied.canInsert();
            boolean output = configured.canExtract() && supplied.canExtract();
            return input
                ? (output ? IoMode.BOTH : IoMode.INPUT)
                : (output ? IoMode.OUTPUT : IoMode.DISABLED);
        });
    }

    private void prepareAdmission(
        zone.moddev.mc.lib.machine.definition.AdmissionDeclaration declaration) {
        if (declaration.type() != null)
            admissionRules.computeIfAbsent(declaration, key -> key.create());
    }

    private zone.moddev.mc.lib.machine.feature.StorageAdmission admission(
        zone.moddev.mc.lib.machine.definition.AdmissionDeclaration declaration) {
        if (declaration.processor() == null)
            return admissionRules.get(declaration);
        return features
            .findProcessor(declaration.processor(),
                zone.moddev.mc.lib.machine.feature.StorageAdmission.class)
            .orElseThrow(() -> new IllegalStateException(
                "Missing admission processor " + declaration.processor()));
    }

    public DefinedMachineEntity(BlockEntityType<?> type, BlockPos pos,
        BlockState state, MachineDefinition definition,
        MachineRecipeType recipes) {
        super(type, pos, state);
        this.definition = definition;
        // Startup validates registered XML; keep direct Java construction
        // equally strict.
        MachineContent.validateResources(definition);
        var power = definition.power();
        var customProcessors = new java.util.HashMap<String, zone.moddev.mc.lib.machine.feature.Processor<?>>();
        if (power != null && power.provider() != null) {
            var config = definition.processors().stream()
                .filter(value -> value.id().equals(power.provider()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                    "Unknown energy provider: " + power.provider()));
            if (config.declaration() == null)
                throw new IllegalArgumentException(
                    "Energy provider must be a custom processor");
            var provider = config.declaration().create(config.id(), this);
            if (!(provider instanceof zone.moddev.mc.lib.machine.feature.ProcessorEnergy source))
                throw new IllegalArgumentException(
                    "Energy provider must implement ProcessorEnergy");
            customProcessors.put(config.id(), provider);
            energyProvider = source;
            energy = java.util.Objects
                .requireNonNull(source.createEnergyStorage(power.capacity(),
                    power.input(), power.output()), "Processor energy storage");
            energy.changes().subscribe(this::setChanged);
        } else {
            energyProvider = null;
            energy = power == null
                ? null
                : new EnergyBuffer(power.capacity(), power.input(),
                    power.output()).onChange(this::setChanged);
        }
        if (power != null && !power.capacitors()
            && definition.behavior().capacitorSlot() != null)
            throw new IllegalArgumentException(
                "Selected energy provider does not enable capacitor upgrades");
        fluids = new FluidStorage().onChange(this::setChanged);
        if (definition.fluids() != null)
            for (var tank : definition.fluids().tanks()) {
                var filter = tank.fluid();
                if (filter != null
                    && !BuiltInRegistries.FLUID.containsKey(filter))
                    throw new IllegalArgumentException(
                        "Unknown tank fluid " + filter);
                int index = fluids.addTank(tank.capacity(), tank.role(),
                    stack -> filter == null || BuiltInRegistries.FLUID
                        .getKey(stack.getFluid()).equals(filter));
                fluids.tankId(index, tank.id());
                if (tank.fluidTag() != null) {
                    var tag = net.minecraft.tags.TagKey.create(
                        net.minecraft.core.registries.Registries.FLUID,
                        tank.fluidTag());
                    fluids.restrict(stack -> stack.is(tag), index);
                }
                if (tank.admission() != null) {
                    prepareAdmission(tank.admission());
                    fluids.restrict(
                        stack -> admission(tank.admission()).acceptsFluid(this,
                            tank.id(),
                            new zone.moddev.mc.lib.machine.ingredient.FluidAmount(
                                stack.getFluidHolder(),
                                stack.getComponentsPatch(), stack.getAmount())),
                        index);
                }
            }
        items = ItemInventories.partition(definition.inventoryNames())
            .onChange(this::setChanged);
        for (int i = 0; i < definition.slots().size(); i++) {
            var slot = definition.slots().get(i);
            items.slotId(i, slot.id()).role(slot.role(), i).limit(slot.limit(),
                i);
            if (slot.admission() != null) {
                prepareAdmission(slot.admission());
                items.restrict(stack -> admission(slot.admission())
                    .acceptsItem(this, slot.id(), stack.copy()), i);
            }
            if (slot.itemTag() != null) {
                var tag = net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.ITEM,
                    slot.itemTag());
                var address = items.address(i);
                items.inventory(address.inventory())
                    .restrict(stack -> stack.is(tag), address.slot());
            }
        }
        if (definition.behavior().legacyUpgradeStorage() != null) {
            String storage = definition.behavior().legacyUpgradeStorage();
            var layout = new java.util.ArrayList<ItemInventories.Address>();
            for (int old = 0; old < 11; old++)
                layout.add(old == 4
                    ? new ItemInventories.Address("mmdlib_gearbox", 0)
                    : old == 5
                        ? new ItemInventories.Address("mmdlib_capacitor", 0)
                        : new ItemInventories.Address(storage,
                            old < 4 ? old : old - 2));
            items.migrateLayout(storage, layout);
        }
        rangeSlots = definition.behavior().rangeSlots().stream()
            .map(id -> java.util.stream.IntStream
                .range(0, definition.slots().size())
                .filter(i -> definition.slots().get(i).id().equals(id))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                    "Unknown range slot: " + id)))
            .toList();
        for (int slot : rangeSlots)
            items.limit(1, slot);
        if (definition.behavior().gearboxSlot() != null) {
            int index = java.util.stream.IntStream
                .range(0, definition.slots().size())
                .filter(i -> definition.slots().get(i).id()
                    .equals(definition.behavior().gearboxSlot()))
                .findFirst().orElseThrow(
                    () -> new IllegalArgumentException("Unknown gearbox slot"));
            var address = items.address(index);
            var socket = items.inventory(address.inventory());
            socket.limit(8, address.slot());
            socket.restrict(stack -> stack
                .getItem() instanceof zone.moddev.mc.lib.common.item.GearboxUpgradeItem,
                address.slot());
            features.gearboxes(() -> {
                var stack = socket.getStackInSlot(address.slot());
                return stack
                    .getItem() instanceof zone.moddev.mc.lib.common.item.GearboxUpgradeItem
                        ? stack.getCount()
                        : 0;
            });
        }
        if (definition.behavior().capacitorSlot() != null) {
            if (energy == null)
                throw new IllegalArgumentException(
                    "Capacitors require energy storage");
            if (definition.behavior().capacitorSlot()
                .equals(definition.behavior().gearboxSlot()))
                throw new IllegalArgumentException(
                    "Gearbox and capacitor require different slots");
            int index = java.util.stream.IntStream
                .range(0, definition.slots().size())
                .filter(i -> definition.slots().get(i).id()
                    .equals(definition.behavior().capacitorSlot()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                    "Unknown capacitor slot"));
            var address = items.address(index);
            var socket = items.inventory(address.inventory());
            socket.limit(8, address.slot());
            socket.restrict(stack -> stack
                .getItem() instanceof zone.moddev.mc.lib.common.item.CapacitorUpgradeItem,
                address.slot());
            var capacitors = new zone.moddev.mc.lib.machine.feature.CapacitorUpgrades(
                energy, power.capacity(), power.input(), power.output(), () -> {
                    var stack = socket.getStackInSlot(address.slot());
                    return stack
                        .getItem() instanceof zone.moddev.mc.lib.common.item.CapacitorUpgradeItem
                            ? stack.getCount()
                            : 0;
                });
            socket.changes().subscribe(capacitors::refresh);
            capacitors.refresh();
        }
        resources = new MachineResources(this, this::facing);
        var highlights = MachineContent.screenHighlights(definition.id());
        for (var group : definition.groups()) {
            if (!group.automation()) {
                resources.recipeOnlyGroup(group.id(),
                    StorageGroups.items(items, group.members().stream()
                        .mapToInt(Integer::intValue).toArray()));
                continue;
            }
            resources
                .items(
                    new ResourceGroup<>(group.id(),
                        definition.groupName(group.id()), group.color(),
                        ResourceType.ITEMS, group.mode(),
                        group.members().stream()
                            .map(definition.inventorySlots(group.id())::indexOf)
                            .toList(),
                        highlights.get(group.id()),
                        new SideConfig(group.mode())),
                    items.inventory(group.id()));
        }
        if (energy != null)
            resources.energy(new ResourceGroup<>("energy",
                definition.groupName(definition.power().id()),
                definition.power().color(), ResourceType.ENERGY, IoMode.INPUT,
                List.of(0), highlights.get(definition.power().id()),
                new SideConfig(IoMode.INPUT)), energy);
        if (definition.fluids() != null)
            for (var group : definition.fluids().groups()) {
                if (!group.automation()) {
                    resources.recipeOnlyGroup(group.id(),
                        StorageGroups.fluids(fluids, group.members().stream()
                            .mapToInt(Integer::intValue).toArray()));
                    continue;
                }
                resources.fluids(new ResourceGroup<>(group.id(),
                    definition.groupName(group.id()), group.color(),
                    ResourceType.FLUIDS, group.mode(), group.members(),
                    highlights.get(group.id()), new SideConfig(group.mode())),
                    fluids);

            }

        for (var ghost : definition.ghosts()) {
            var samples = new zone.moddev.mc.lib.machine.inventory.GhostItemGroup(
                ghost.count());
            if (ghost.defaultItem() != null) {
                for (int i = 0; i < ghost.count(); i++)
                    samples.set(i, new net.minecraft.world.item.ItemStack(
                        BuiltInRegistries.ITEM.get(ghost.defaultItem())));
            }
            ghosts.put(ghost.id(), samples);
            resources.recipeOnlyGroup(ghost.id(), samples);
        }
        resources.build();
        var runtimeProcessors = new java.util.LinkedHashMap<String, zone.moddev.mc.lib.machine.feature.Processor<?>>();
        for (var config : definition.processors()) {
            zone.moddev.mc.lib.machine.feature.Processor<?> value;
            if (config.declaration() != null)
                value = customProcessors.computeIfAbsent(config.id(),
                    ignored -> config.declaration().create(config.id(), this));
            else {
                var typeForProcessor = recipeType(config, recipes);
                var lane = new RecipeProcessor(config.id(), typeForProcessor,
                    resources.recipes().view(config.bindings()),
                    Component.literal(config.name()));
                if (config.selection() != null)
                    lane.selectable(config.selection().defaultRecipe());
                bindCycleCallbacks(lane, config);
                value = lane;
            }
            features.add(value);
            runtimeProcessors.put(config.id(), value);
            if (value instanceof zone.moddev.mc.lib.machine.feature.WorkTargets.Provider provider) {
                var targets = new zone.moddev.mc.lib.machine.feature.WorkTargets(
                    config.id().equals(definition.processor())
                        ? "mmdlib:work_targets"
                        : "mmdlib:work_targets/" + config.id(),
                    provider::workingBlocks, value::processing);
                workTargets.put(config.id(), targets);
                features.add(targets);
            }
        }
        installRecipeAdmission(recipes);
        inventory = genericInventory(resources.automation());
        if (definition.itemStorageId() != null)
            inventory.items(definition.itemStorageId(), items);
        if (definition.fluids() != null)
            inventory.fluids(definition.fluids().id(), fluids);
        redstone = definition.redstone()
            || definition.behavior().comparatorSource() != null
                ? new RedstoneControl(this, List.of(features))
                    .comparatorSource(definition.behavior().comparatorSource())
                : null;
        if (redstone != null && !definition.redstone())
            redstone.disabledFaces(java.util.EnumSet
                .allOf(zone.moddev.mc.lib.api.capabilities.RelativeFace.class));

        var graph = new VisualVariables[1];
        java.util.function.Supplier<VisualVariables> sharedVariables = () -> {
            if (graph[0] == null)
                graph[0] = BuiltinVisualVariables.forProcessors(definition,
                    runtimeProcessors, items, fluids, energy);
            return graph[0];
        };
        features.bindVisualVariables(sharedVariables);
        var emptyVariables = new VisualVariables.Builder().build();
        visuals = new MachineVisualState(!definition.hasConditionalAppearance()
            ? emptyVariables
            : sharedVariables.get(), definition.visualConditions());
        shapes = new MachineShapeState(definition.behavior().shapes(),
            definition.behavior().shapes().isEmpty()
                ? emptyVariables
                : sharedVariables.get(),
            () -> {
                if (level == null)
                    return;
                if (level.isClientSide)
                    level.sendBlockUpdated(worldPosition, getBlockState(),
                        getBlockState(),
                        net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                else if (!isRemoved())
                    FeatureSync.send(this);
            });
    }

    public net.minecraft.world.phys.shapes.VoxelShape shape(
        net.minecraft.core.Direction facing) {
        return shapes.shape(facing);
    }

    public Level level() {
        return getLevel();
    }
    public BlockPos position() {
        return getBlockPos();
    }
    public BlockState blockState() {
        return getBlockState();
    }
    public net.minecraft.world.item.ItemStack item() {
        var stack = new net.minecraft.world.item.ItemStack(
            getBlockState().getBlock());
        stack.applyComponents(collectComponents());
        return stack;
    }
    @Override
    public boolean authorizePickup(
        net.minecraft.world.entity.player.Player player) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
            || level == null || level.isClientSide || player.level() != level
            || !player.mayBuild() || !level.mayInteract(player, worldPosition)
            || isRemoved() || level.getBlockEntity(worldPosition) != this)
            return false;
        var event = net.neoforged.neoforge.common.CommonHooks.fireBlockBreak(
            level, serverPlayer.gameMode.getGameModeForPlayer(), serverPlayer,
            worldPosition, getBlockState());
        return !event.isCanceled() && !isRemoved()
            && level.getBlockEntity(worldPosition) == this;
    }
    public void remove() {
        if (level != null)
            level.removeBlock(worldPosition, false);
    }
    public boolean removed() {
        return isRemoved();
    }
    void afterPlacement(Runnable action) {
        // NeoForge confirms captured placements through Block.onPlace only
        // after the placement event accepts them.
        if (level != null && level.captureBlockSnapshots) {
            var previous = placementComplete;
            placementComplete = () -> {
                if (previous != null)
                    previous.run();
                action.run();
            };
        } else
            action.run();
    }
    void completePlacement() {
        var action = placementComplete;
        placementComplete = null;
        if (action != null && level != null && !isRemoved()
            && level.getBlockEntity(worldPosition) == this)
            action.run();
    }
    void notifyPlacement(net.minecraft.world.entity.player.Player placer,
        net.minecraft.world.item.ItemStack item) {
        if (placementNotified || level == null || level.isClientSide()
            || isRemoved())
            return;
        placementNotified = true;
        var placement = new zone.moddev.mc.lib.machine.feature.MachinePlacement(
            level, worldPosition, getBlockState(), placer, item);
        features.allProcessors()
            .forEach(processor -> processor.placed(placement));
        setChanged();
    }

    public void changed() {
        changed(true);
    }
    @Override
    public List<net.minecraft.world.item.ItemStack> inventoryContents(
        String name) {
        var storage = items.inventory(name);
        return java.util.stream.IntStream.range(0, storage.getSlots())
            .mapToObj(slot -> storage.getStackInSlot(slot).copy()).toList();
    }
    public void changed(boolean synchronize) {
        setChanged();
        if (!synchronize)
            return;
        features.markSync();
    }
    public zone.moddev.mc.lib.machine.feature.PortableProcessor portable() {
        if (!definition.behavior().portable())
            return null;
        return features
            .findProcessor(definition.processor(),
                zone.moddev.mc.lib.machine.feature.PortableProcessor.class)
            .orElse(null);
    }
    @Override
    public boolean screenStatsDrawer() {
        return definition.behavior().drawers();
    }
    @Override
    public boolean screenAutomationAddons() {
        return definition.behavior().drawers();
    }
    @Override
    public zone.moddev.mc.lib.api.automation.AutomationInventory screenAutomation() {
        return definition.behavior().drawers()
            ? super.screenAutomation()
            : null;
    }
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide() && !definition.behavior()
            .facing().directions().contains(facing())) {
            var property = getBlockState().hasProperty(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)
                    ? net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING
                    : net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
            level.setBlock(worldPosition, getBlockState().setValue(property,
                definition.behavior().facing().defaultDirection()), 3);
        }
        if (level != null && !level.isClientSide)
            features
                .findProcessor(definition.processor(),
                    zone.moddev.mc.lib.machine.feature.StoredProcessor.class)
                .ifPresent(
                    zone.moddev.mc.lib.machine.feature.StoredProcessor::refreshDerived);
        if (legacyGhostRecipe != null && level != null && !level.isClientSide) {
            migrateGhostRecipe(legacyGhostRecipe, level.registryAccess());
            legacyGhostRecipe = null;
            setChanged();
        }
        if (level != null && !level.isClientSide)
            visuals.start(() -> level.scheduleTick(worldPosition,
                getBlockState().getBlock(), 1));
    }

    @Override
    public void setRemoved() {
        visuals.stop();
        super.setRemoved();
    }

    void flushVisuals() {
        visuals.flush(this);
    }

    @Override
    protected void saveClientData(CompoundTag tag,
        net.minecraft.core.HolderLookup.Provider registries) {
        tag.put("visuals", visuals.write());
        saveGhosts(tag, registries);
    }

    @Override
    protected void loadClientData(CompoundTag tag,
        net.minecraft.core.HolderLookup.Provider registries) {
        shapes.refresh();
        visuals.read(tag.getCompound("visuals"), this);
        readGhosts(tag, registries);
    }

    @Override
    public net.minecraft.world.item.ItemStack screenGhost(String inventory,
        int slot) {
        var group = ghosts.get(inventory);
        return group == null
            ? net.minecraft.world.item.ItemStack.EMPTY
            : group.peek(slot);
    }

    @Override
    public boolean setScreenGhost(
        net.minecraft.world.entity.player.Player player, String inventory,
        int slot, net.minecraft.world.item.ItemStack sample) {
        var group = ghosts.get(inventory);
        if (group == null || (level != null && level.isClientSide)
            || !group.set(slot, sample))
            return false;
        setChanged();
        features.markSync();
        return true;
    }

    private void saveGhosts(CompoundTag tag,
        net.minecraft.core.HolderLookup.Provider registries) {
        var saved = new CompoundTag();
        ghosts.forEach((id, group) -> saved.put(id, group.save(registries)));
        tag.put("mmdlib_ghost_items", saved);
    }

    private void readGhosts(CompoundTag tag,
        net.minecraft.core.HolderLookup.Provider registries) {
        var saved = tag.getCompound("mmdlib_ghost_items");
        ghosts.forEach((id, group) -> {
            if (saved.contains(id))
                group.load(saved.getCompound(id), registries);
        });
    }

    @Override
    protected void saveMachineData(CompoundTag tag,
        net.minecraft.core.HolderLookup.Provider registries) {
        var metadata = tag.getCompound("mmdlib_machine");
        saveGhosts(metadata, registries);
        tag.put("mmdlib_machine", metadata);
    }

    @Override
    protected void loadMachineData(CompoundTag tag,
        net.minecraft.core.HolderLookup.Provider registries) {
        shapes.refresh();
        legacyGhostRecipe = null;
        for (var ghost : definition.ghosts()) {
            var sample = ghost.defaultItem() == null
                ? net.minecraft.world.item.ItemStack.EMPTY
                : new net.minecraft.world.item.ItemStack(
                    BuiltInRegistries.ITEM.get(ghost.defaultItem()));
            for (int i = 0; i < ghost.count(); i++)
                ghosts.get(ghost.id()).set(i, sample);
        }
        var metadata = tag.getCompound("mmdlib_machine");
        // Pre-release ghost saves used a top-level key which could also be a
        // consumer storage ID.
        if (!metadata.contains("mmdlib_ghost_items")
            && !"mmdlib_ghost_items".equals(definition.itemStorageId())
            && (definition.fluids() == null
                || !definition.fluids().id().equals("mmdlib_ghost_items")))
            metadata = tag;
        readGhosts(metadata, registries);
        // Migrate the earlier output selector, including an explicitly cleared
        // selection.
        if (!definition.hasProcessor())
            return;
        var old = tag.getCompound("features")
            .getCompound(definition.processor());
        if (!metadata.contains("mmdlib_ghost_items")
            && old.contains("selection") && ghosts.size() == 1
            && ghosts.values().iterator().next().size() == 1) {
            var id = ResourceLocation
                .tryParse(old.getCompound("selection").getString("recipe"));
            ghosts.values().iterator().next().set(0,
                net.minecraft.world.item.ItemStack.EMPTY);
            if (id != null) {
                if (level == null)
                    legacyGhostRecipe = id;
                else
                    migrateGhostRecipe(id, registries);
            }
        }
    }

    private void migrateGhostRecipe(ResourceLocation id,
        net.minecraft.core.HolderLookup.Provider registries) {
        var recipe = level.getRecipeManager().byKey(id).orElse(null);
        if (recipe != null && recipe
            .value() instanceof zone.moddev.mc.lib.machine.recipe.MachineRecipeEntry entry)
            ghosts.values().iterator().next().set(0,
                entry.getResultItem(registries));
    }

    @Override
    public net.neoforged.neoforge.client.model.data.ModelData getModelData() {
        return visuals.modelData();
    }

    /**
     * Whether a cycle is running; on a client, as of the last state the server
     * sent.
     */
    public boolean running() {
        return features.processors().stream().anyMatch(
            value -> value.status() == RecipeProcessor.Status.RUNNING);
    }

    private void bindCycleCallbacks(RecipeProcessor lane,
        zone.moddev.mc.lib.machine.definition.ProcessorConfiguration config) {
        lane.cycleCallbacks(cycle -> {
            if (level instanceof net.minecraft.server.level.ServerLevel server)
                config.events().start().ifPresent(id -> fire(id,
                    zone.moddev.mc.lib.api.MaterialEventTypes.MACHINE_CYCLE_START,
                    server, config.id(), cycle));
        }, cycle -> {
            if (!(level instanceof net.minecraft.server.level.ServerLevel server))
                return;
            config.sounds().finish()
                .ifPresent(sound -> server.playSound(null, worldPosition,
                    SoundDefinition.event(sound), SoundSource.BLOCKS, 1, 1));
            config.events().finish().ifPresent(id -> fire(id,
                zone.moddev.mc.lib.api.MaterialEventTypes.MACHINE_CYCLE_FINISH,
                server, config.id(), cycle));
        });
    }

    private void fire(ResourceLocation id,
        zone.moddev.mc.lib.api.MaterialEvent<zone.moddev.mc.lib.api.MaterialEventContexts.MachineCycle> event,
        net.minecraft.server.level.ServerLevel server, String processor,
        zone.moddev.mc.lib.machine.recipe.CyclePlan cycle) {
        zone.moddev.mc.lib.api.MaterialExtensions.GLOBAL.events().dispatch(id,
            event,
            new zone.moddev.mc.lib.api.MaterialEventContexts.MachineCycle(this,
                server, worldPosition, definition.id(), cycle.recipe(),
                cycle.time(), processor));
    }

    /**
     * Keeps the {@code running-sound} looping while a cycle runs; called each
     * client tick.
     */
    public void clientTick() {
        zone.moddev.mc.lib.client.MachineSoundsClient.tick(this);
        var active = workTargets.entrySet().stream()
            .filter(entry -> features.findProcessor(entry.getKey())
                .map(zone.moddev.mc.lib.machine.feature.Processor::processing)
                .orElse(false))
            .flatMap(entry -> entry.getValue().targets().stream()).distinct()
            .toList();
        if (!active.isEmpty())
            zone.moddev.mc.lib.client.MachineWorkDisplay.tick(this, active,
                visualizerStyle().workColor());
    }

    public zone.moddev.mc.lib.machine.presentation.VisualizerStyle visualizerStyle() {
        return definition.behavior().visualizers();
    }

    /**
     * The synchronized active target snapshot; empty for processors without a
     * provider.
     */
    public List<BlockPos> workingBlocks() {
        return workTargets.values().stream()
            .flatMap(targets -> targets.targets().stream()).distinct().toList();
    }

    @Override
    protected EnergyBuffer powerStorage() {
        return energy;
    }

    @Override
    public int rangeUpgrades() {
        return zone.moddev.mc.lib.machine.feature.RangeUpgrades
            .count(rangeSlots.stream().map(items::getStackInSlot).toList());
    }

    @Override
    public FluidStorage screenFluids() {
        return fluids;
    }

    @Override
    public java.util.Optional<Component> screenTankName(int tank) {
        if (definition.fluids() == null)
            return java.util.Optional.empty();
        return definition.fluids().groups().stream()
            .filter(group -> group.members().contains(tank))
            .map(group -> definition.groupName(group.id())).findFirst();
    }

    @Override
    public String screenDefinitionFingerprint() {
        return definition.checksum();
    }

    @Override
    public ItemInventories screenItems() {
        return items;
    }

    @Override
    public RedstoneControl screenRedstone() {
        return redstone;
    }

    @Override
    protected MachineInventoryLifecycle machineInventory() {
        return inventory;
    }

    @Override
    public int[] screenRecipeSlots(ResourceLocation category) {
        if (!definition.processes(category))
            return new int[0];
        var targets = new java.util.HashSet<String>();
        for (var config : definition.processors())
            if (config.recipeType() != null
                && config.recipeType().id().equals(category)) {
                if (config.bindings().isEmpty())
                    definition.groups()
                        .forEach(group -> targets.add(group.id()));
                else
                    targets.addAll(config.bindings().values());
            }
        return definition.groups().stream()
            .filter(g -> g.mode() == IoMode.INPUT && targets.contains(g.id()))
            .flatMap(g -> g.members().stream()).distinct()
            .mapToInt(Integer::intValue).toArray();
    }

    @Override
    protected void recoverResources(Level level, BlockPos pos,
        CompoundTag payload) {
        net.minecraft.world.Containers.dropItemStack(level, pos.getX() + .5,
            pos.getY() + .5, pos.getZ() + .5,
            MachineRecoveryItem.create(payload));
    }
}
