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
import zone.moddev.mc.lib.machine.recipe.MachineRecipeType;

/** Shared runtime assembled from XML. There is no per-machine Java subclass. */
public final class DefinedMachineEntity extends BaseMachine {
    public final MachineDefinition definition;
    public final ItemInventories items;
    public final FluidStorage fluids;
    public final EnergyBuffer energy;
    public final MachineResources resources;
    public final MachineInventoryLifecycle inventory;
    private final RedstoneControl redstone;
    private final RecipeProcessor processor;
    private java.math.BigInteger cyclesBefore = java.math.BigInteger.ZERO;
    private long startsBefore;

    public DefinedMachineEntity(BlockEntityType<?> type, BlockPos pos,
        BlockState state, MachineDefinition definition,
        MachineRecipeType recipes) {
        super(type, pos, state);
        this.definition = definition;
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
                        Component.literal(group.id()), group.color(),
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
                Component.literal("Energy"), definition.power().color(),
                ResourceType.ENERGY, IoMode.INPUT, List.of(0),
                highlights.get(definition.power().id()),
                new SideConfig(IoMode.INPUT)), energy);
        if (definition.fluids() != null)
            for (var group : definition.fluids().groups()) {
                resources.fluids(new ResourceGroup<>(group.id(),
                    Component.literal(group.id()), group.color(),
                    ResourceType.FLUIDS, group.mode(), group.members(),
                    highlights.get(group.id()), new SideConfig(group.mode())),
                    fluids);
                if (group.mode() == IoMode.INPUT)
                    resources.recipeInputs(recipes, group.id());
            }
        for (var group : definition.groups())
            if (group.mode() == IoMode.INPUT)
                resources.recipeInputs(recipes, group.id());
        resources.build();
        processor = new RecipeProcessor(definition.processor(), recipes,
            resources.recipes(), Component.literal(definition.processorName()));
        features.add(processor);
        inventory = genericInventory(resources.automation())
            .items(definition.itemStorageId(), items);
        if (definition.fluids() != null)
            inventory.fluids(definition.fluids().id(), fluids);
        redstone = definition.redstone()
            ? new RedstoneControl(this, List.of(features))
            : null;
    }

    /**
     * Whether a cycle is running; on a client, as of the last state the server
     * sent.
     */
    public boolean running() {
        return processor.status() == RecipeProcessor.Status.RUNNING;
    }

    @Override
    protected void beforeMachineTick() {
        cyclesBefore = processor.completedCycles();
        startsBefore = processor.startedCycles();
    }

    /**
     * Runs the processor's {@code on-start} and {@code on-finish} handlers, and
     * plays the {@code finish-sound} for everyone near a completed cycle.
     */
    @Override
    protected void afterMachineTick() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server))
            return;
        if (processor.startedCycles() != startsBefore)
            definition.events().start()
                .ifPresent(id -> processor.cycle().ifPresent(cycle -> fire(id,
                    zone.moddev.mc.lib.api.MaterialEventTypes.MACHINE_CYCLE_START,
                    server, cycle)));
        if (processor.completedCycles().equals(cyclesBefore))
            return;
        definition.sounds().finish()
            .ifPresent(sound -> level.playSound(null, worldPosition,
                SoundDefinition.event(sound), SoundSource.BLOCKS, 1, 1));
        definition.events().finish().ifPresent(id -> processor.justFinished()
            .ifPresent(cycle -> fire(id,
                zone.moddev.mc.lib.api.MaterialEventTypes.MACHINE_CYCLE_FINISH,
                server, cycle)));
    }

    private void fire(ResourceLocation id,
        zone.moddev.mc.lib.api.MaterialEvent<zone.moddev.mc.lib.api.MaterialEventContexts.MachineCycle> event,
        net.minecraft.server.level.ServerLevel server,
        zone.moddev.mc.lib.machine.recipe.CyclePlan cycle) {
        zone.moddev.mc.lib.api.MaterialExtensions.GLOBAL.events().dispatch(id,
            event,
            new zone.moddev.mc.lib.api.MaterialEventContexts.MachineCycle(this,
                server, worldPosition, definition.id(), cycle.recipe(),
                cycle.time()));
    }

    /**
     * Keeps the {@code running-sound} looping while a cycle runs; called each
     * client tick.
     */
    public void clientTick() {
        if (definition.sounds().running().isPresent())
            zone.moddev.mc.lib.client.MachineSoundsClient.tick(this);
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
        if (!definition.recipeType().id().equals(category))
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
