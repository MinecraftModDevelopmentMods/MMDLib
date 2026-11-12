package zone.moddev.mc.companion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import zone.moddev.mc.lib.api.capabilities.FluidStorage;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.api.capabilities.Role;
import zone.moddev.mc.lib.machine.multiblock.MultiblockInstance;
import zone.moddev.mc.lib.machine.neoforge.BasePoweredMachine;
import zone.moddev.mc.lib.machine.neoforge.MultiblockMembers;
import zone.moddev.mc.lib.machine.neoforge.MultiblockResources;

/**
 * Minimal consumer; its counter proves that structure holds also gate custom
 * machine hooks.
 */
public final class MultiblockDemoEntity extends BasePoweredMachine
    implements MultiblockResources {
    public final ItemStorage items = new ItemStorage(1)
        .onChange(this::setChanged);
    public final FluidStorage fluids = new FluidStorage()
        .onChange(this::setChanged);
    private final MultiblockInstance structure;
    private int operations;
    public MultiblockDemoEntity(BlockPos pos, BlockState state) {
        this(pos, state, MultiblockDemo.definition());
    }

    public MultiblockDemoEntity(BlockPos pos, BlockState state,
        zone.moddev.mc.lib.machine.multiblock.MultiblockDefinition definition) {
        super(MultiblockDemo.CONTROLLER_TYPE.get(), pos, state, 10000, 1000,
            1000);
        fluids.addTank(4000, Role.STORAGE, stack -> true);
        structure = new MultiblockInstance(this, definition,
            MultiblockMembers.INSTANCE, new MultiblockInstance.Listener() {
                @Override
                public void dismantling(MultiblockInstance.Cause cause) {
                    features.reset();
                    if (definition
                        .dismantling() == zone.moddev.mc.lib.machine.multiblock.MultiblockDefinition.Dismantling.LEAVE_BLOCKS)
                        return;
                    items.dropAll(level, worldPosition,
                        payload -> recoverResources(level, worldPosition,
                            payload));
                    if (cause == MultiblockInstance.Cause.SURVIVAL)
                        net.minecraft.world.Containers.dropItemStack(level,
                            worldPosition.getX() + .5,
                            worldPosition.getY() + .5,
                            worldPosition.getZ() + .5,
                            new ItemStack(MultiblockDemo.ITEM.get()));
                }
            });
    }
    @Override
    public MultiblockInstance multiblock() {
        return structure;
    }
    @Override
    public ItemStorage screenItems() {
        return items;
    }
    @Override
    public FluidStorage screenFluids() {
        return fluids;
    }
    @Override
    public boolean screenAutomationAddons() {
        return false;
    }
    public net.minecraft.network.chat.Component getStructureStatus() {
        return net.minecraft.network.chat.Component
            .literal(structure.status().name());
    }
    public int getOperations() {
        return operations;
    }
    @Override
    protected void beforeMachineTick() {
        operations++;
        energy.insert(1, false);
        setChanged();
    }
    @Override
    protected void saveMachineData(CompoundTag tag,
        HolderLookup.Provider registries) {
        tag.put("items", items.serializeNBT(registries));
        tag.put("fluids", fluids.serializeNBT(registries));
        tag.putInt("operations", operations);
    }
    @Override
    protected void loadMachineData(CompoundTag tag,
        HolderLookup.Provider registries) {
        items.deserializeNBT(registries, tag.getCompound("items"));
        fluids.deserializeNBT(registries, tag.getCompound("fluids"));
        operations = tag.getInt("operations");
    }
    @Override
    protected void recoverResources(Level world, BlockPos pos,
        CompoundTag payload) {
        net.minecraft.world.Containers.dropItemStack(world, pos.getX() + .5,
            pos.getY() + .5, pos.getZ() + .5,
            RecoveryCapsuleItem.create(payload));
    }
    @Override
    public IItemHandler multiblockItems(String resource, Direction side) {
        return resource.equals("items") ? items.view(IoMode.BOTH) : null;
    }
    @Override
    public IFluidHandler multiblockFluids(String resource, Direction side) {
        return resource.equals("fluid") ? fluids.view(IoMode.BOTH) : null;
    }
    @Override
    public IEnergyStorage multiblockEnergy(String resource, Direction side) {
        return resource.equals("energy") ? energy.view(IoMode.BOTH) : null;
    }
}
