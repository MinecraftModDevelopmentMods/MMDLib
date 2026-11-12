package zone.moddev.mc.lib.machine.neoforge;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import zone.moddev.mc.lib.machine.multiblock.MemberLink;
import zone.moddev.mc.lib.machine.multiblock.MultiblockInstance;
import zone.moddev.mc.lib.machine.multiblock.StructurePort;

/**
 * Formation-bound capability adapters. Retained wrappers become inert after
 * unlink/reformation.
 */
public final class MultiblockPorts {
    private MultiblockPorts() {
    }
    public static void register(RegisterCapabilitiesEvent event,
        BlockEntityType<?>... types) {
        for (var type : types) {
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type,
                MultiblockPorts::items);
            event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type,
                MultiblockPorts::fluids);
            event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type,
                MultiblockPorts::energy);
        }
    }
    private record Binding(MultiblockInstance instance, MemberLink link,
        BlockPos position, Direction side, StructurePort port, UUID formation,
        long revision) {
        MultiblockResources resources() {
            var level = instance.owner().getLevel();
            if (level == null || level.isClientSide
                || !level.hasChunkAt(instance.owner().getBlockPos())
                || level.getBlockEntity(
                    instance.owner().getBlockPos()) != instance.owner()
                || !formation.equals(instance.formation())
                || revision != instance.revision()
                || !instance.owns(position, link) || !instance.ready())
                return null;
            return instance.owner() instanceof MultiblockResources resources
                ? resources
                : null;
        }
        IItemHandler items() {
            var r = resources();
            return r == null ? null : r.multiblockItems(port.resource(), side);
        }
        IFluidHandler fluids() {
            var r = resources();
            return r == null ? null : r.multiblockFluids(port.resource(), side);
        }
        IEnergyStorage energy() {
            var r = resources();
            return r == null ? null : r.multiblockEnergy(port.resource(), side);
        }
    }
    private static Binding binding(BlockEntity entity, Direction side,
        StructurePort.Kind kind) {
        var level = entity.getLevel();
        if (level == null || level.isClientSide)
            return null;
        var instance = MultiblockMembers.resolve(level, entity.getBlockPos());
        if (instance == null || !instance.ready())
            return null;
        var link = MultiblockMembers.INSTANCE.link(level, entity.getBlockPos());
        for (var port : instance.definition().ports())
            if (port.kind() == kind && port.member().equals(link.member())
                && port.exposes(
                    side == null ? null : instance.transform().local(side)))
                return new Binding(instance, link, entity.getBlockPos(), side,
                    port, instance.formation(), instance.revision());
        return null;
    }
    public static IItemHandler items(BlockEntity entity, Direction side) {
        var binding = binding(entity, side, StructurePort.Kind.ITEMS);
        if (binding == null || binding.items() == null)
            return null;
        return new IItemHandler() {
            private boolean slot(IItemHandler handler, int slot) {
                return handler != null && slot >= 0
                    && slot < handler.getSlots();
            }
            @Override
            public int getSlots() {
                var h = binding.items();
                return h == null ? 0 : h.getSlots();
            }
            @Override
            public ItemStack getStackInSlot(int slot) {
                var h = binding.items();
                return slot(h, slot)
                    ? h.getStackInSlot(slot).copy()
                    : ItemStack.EMPTY;
            }
            @Override
            public ItemStack insertItem(int slot, ItemStack stack,
                boolean simulate) {
                var h = binding.items();
                return binding.port.input() && slot(h, slot)
                    ? h.insertItem(slot, stack, simulate)
                    : stack;
            }
            @Override
            public ItemStack extractItem(int slot, int amount,
                boolean simulate) {
                var h = binding.items();
                return binding.port.output() && slot(h, slot) && amount > 0
                    ? h.extractItem(slot, amount, simulate)
                    : ItemStack.EMPTY;
            }
            @Override
            public int getSlotLimit(int slot) {
                var h = binding.items();
                return slot(h, slot) ? h.getSlotLimit(slot) : 0;
            }
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                var h = binding.items();
                return binding.port.input() && slot(h, slot)
                    && h.isItemValid(slot, stack);
            }
        };
    }
    public static IFluidHandler fluids(BlockEntity entity, Direction side) {
        var binding = binding(entity, side, StructurePort.Kind.FLUIDS);
        if (binding == null || binding.fluids() == null)
            return null;
        return new IFluidHandler() {
            private boolean tank(IFluidHandler h, int tank) {
                return h != null && tank >= 0 && tank < h.getTanks();
            }
            @Override
            public int getTanks() {
                var h = binding.fluids();
                return h == null ? 0 : h.getTanks();
            }
            @Override
            public FluidStack getFluidInTank(int tank) {
                var h = binding.fluids();
                return tank(h, tank)
                    ? h.getFluidInTank(tank).copy()
                    : FluidStack.EMPTY;
            }
            @Override
            public int getTankCapacity(int tank) {
                var h = binding.fluids();
                return tank(h, tank) ? h.getTankCapacity(tank) : 0;
            }
            @Override
            public boolean isFluidValid(int tank, FluidStack stack) {
                var h = binding.fluids();
                return binding.port.input() && tank(h, tank)
                    && h.isFluidValid(tank, stack);
            }
            @Override
            public int fill(FluidStack stack, FluidAction action) {
                var h = binding.fluids();
                return h != null && binding.port.input()
                    ? h.fill(stack, action)
                    : 0;
            }
            @Override
            public FluidStack drain(FluidStack stack, FluidAction action) {
                var h = binding.fluids();
                return h != null && binding.port.output()
                    ? h.drain(stack, action)
                    : FluidStack.EMPTY;
            }
            @Override
            public FluidStack drain(int amount, FluidAction action) {
                var h = binding.fluids();
                return h != null && binding.port.output() && amount > 0
                    ? h.drain(amount, action)
                    : FluidStack.EMPTY;
            }
        };
    }
    public static IEnergyStorage energy(BlockEntity entity, Direction side) {
        var binding = binding(entity, side, StructurePort.Kind.ENERGY);
        if (binding == null || binding.energy() == null)
            return null;
        return new IEnergyStorage() {
            @Override
            public int receiveEnergy(int amount, boolean simulate) {
                var h = binding.energy();
                return h != null && binding.port.input() && amount > 0
                    ? h.receiveEnergy(amount, simulate)
                    : 0;
            }
            @Override
            public int extractEnergy(int amount, boolean simulate) {
                var h = binding.energy();
                return h != null && binding.port.output() && amount > 0
                    ? h.extractEnergy(amount, simulate)
                    : 0;
            }
            @Override
            public int getEnergyStored() {
                var h = binding.energy();
                return h == null ? 0 : h.getEnergyStored();
            }
            @Override
            public int getMaxEnergyStored() {
                var h = binding.energy();
                return h == null ? 0 : h.getMaxEnergyStored();
            }
            @Override
            public boolean canExtract() {
                var h = binding.energy();
                return h != null && binding.port.output() && h.canExtract();
            }
            @Override
            public boolean canReceive() {
                var h = binding.energy();
                return h != null && binding.port.input() && h.canReceive();
            }
        };
    }
}
