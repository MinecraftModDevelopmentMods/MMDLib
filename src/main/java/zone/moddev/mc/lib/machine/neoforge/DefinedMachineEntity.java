package zone.moddev.mc.lib.machine.neoforge;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
import zone.moddev.mc.lib.machine.definition.MachineDefinition;
import zone.moddev.mc.lib.machine.feature.RecipeProcessor;
import zone.moddev.mc.lib.machine.presentation.BuiltinVisualVariables;
import zone.moddev.mc.lib.machine.presentation.VisualVariables;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeType;

/** Shared runtime assembled from XML. There is no per-machine Java subclass. */
public final class DefinedMachineEntity extends BaseMachine
    implements zone.moddev.mc.lib.machine.feature.ProcessorMachine {
    public final MachineDefinition definition;
    private Runnable placementComplete;
    public final ItemInventories items;
    public final FluidStorage fluids;
    public final EnergyBuffer energy;
    public final MachineResources resources;
    public final MachineInventoryLifecycle inventory;
    private final RedstoneControl redstone;
    private final MachineVisualState visuals;
    private ResourceLocation legacyGhostRecipe;
    private final java.util.Map<String, zone.moddev.mc.lib.machine.inventory.GhostItemGroup> ghosts = new java.util.LinkedHashMap<>();

    public DefinedMachineEntity(BlockEntityType<?> type, BlockPos pos,
        BlockState state, MachineDefinition definition,
        MachineRecipeType recipes) {
        super(type, pos, state);
        this.definition = definition;
        // Startup validates registered XML; keep direct Java construction
        // equally strict.
        MachineContent.validateResources(definition);
        var power = definition.power();
        energy = power == null
            ? null
            : new EnergyBuffer(power.capacity(), power.input(), power.output())
                .onChange(this::setChanged);
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
            }
        items = ItemInventories.partition(definition.inventoryNames())
            .onChange(this::setChanged);
        for (int i = 0; i < definition.slots().size(); i++) {
            var slot = definition.slots().get(i);
            items.slotId(i, slot.id()).role(slot.role(), i).limit(slot.limit(),
                i);
        }
        resources = new MachineResources(this, this::facing);
        var highlights = MachineContent.screenHighlights(definition.id());
        for (var group : definition.groups()) {
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
                if (recipes != null && group.mode() == IoMode.INPUT)
                    resources.recipeInputs(recipes, group.id());
            }
        for (var group : definition.groups())
            if (recipes != null && group.mode() == IoMode.INPUT)
                resources.recipeInputs(recipes, group.id());
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
        zone.moddev.mc.lib.machine.feature.Processor<?> visualProcessor = null;
        RecipeProcessor processor = null;
        if (definition.behavior().processor() != null) {
            visualProcessor = definition.behavior().processor()
                .create(definition.processor(), this);
            features.add(visualProcessor);
        } else if (definition.hasProcessor()) {
            processor = new RecipeProcessor(definition.processor(), recipes,
                resources.recipes(),
                Component.literal(definition.processorName()));
            if (definition.recipeSelection() != null)
                processor
                    .selectable(definition.recipeSelection().defaultRecipe());
            features.add(processor);
            visualProcessor = processor;
        }
        inventory = genericInventory(resources.automation());
        if (definition.itemStorageId() != null)
            inventory.items(definition.itemStorageId(), items);
        if (definition.fluids() != null)
            inventory.fluids(definition.fluids().id(), fluids);
        redstone = definition.redstone()
            ? new RedstoneControl(this, List.of(features))
            : null;
        visuals = new MachineVisualState(!definition.hasConditionalAppearance()
            ? new VisualVariables.Builder().build()
            : BuiltinVisualVariables.forMachine(definition, visualProcessor,
                items, fluids, energy),
            definition.visualConditions());
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
        return new net.minecraft.world.item.ItemStack(
            getBlockState().getBlock());
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
        if (level != null && level.captureBlockSnapshots)
            placementComplete = action;
        else
            action.run();
    }
    void completePlacement() {
        var action = placementComplete;
        placementComplete = null;
        if (action != null)
            action.run();
    }
    public void changed() {
        changed(true);
    }
    public void changed(boolean synchronize) {
        setChanged();
        if (!synchronize)
            return;
        features.markSync();
        if (level != null && !level.isClientSide && !isRemoved()) {
            // Physical bounds and tracking-player state must change together,
            // without the normal batching delay.
            if (!definition.behavior().shapes().isEmpty())
                FeatureSync.send(this);
        }
    }
    public zone.moddev.mc.lib.machine.feature.PortableProcessor portable() {
        if (!definition.behavior().portable())
            return null;
        return features.findProcessor(definition.processor()).filter(
            zone.moddev.mc.lib.machine.feature.PortableProcessor.class::isInstance)
            .map(
                zone.moddev.mc.lib.machine.feature.PortableProcessor.class::cast)
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
    public void dropContents(Level world, BlockPos pos) {
        var portable = portable();
        if (portable != null) {
            var stack = portable.take();
            if (!stack.isEmpty())
                net.minecraft.world.Containers.dropItemStack(world,
                    pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, stack);
        }
        super.dropContents(world, pos);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide)
            features.findProcessor(definition.processor()).filter(
                zone.moddev.mc.lib.machine.feature.StoredProcessor.class::isInstance)
                .map(
                    zone.moddev.mc.lib.machine.feature.StoredProcessor.class::cast)
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

    @Override
    protected EnergyBuffer powerStorage() {
        return energy;
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
        return definition.groups().stream()
            .filter(g -> g.mode() == IoMode.INPUT)
            .flatMap(g -> g.members().stream()).mapToInt(Integer::intValue)
            .toArray();
    }

    @Override
    protected void recoverResources(Level level, BlockPos pos,
        CompoundTag payload) {
        net.minecraft.world.Containers.dropItemStack(level, pos.getX() + .5,
            pos.getY() + .5, pos.getZ() + .5,
            MachineRecoveryItem.create(payload));
    }
}
