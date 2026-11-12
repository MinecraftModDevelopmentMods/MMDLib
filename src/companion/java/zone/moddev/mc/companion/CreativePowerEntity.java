package zone.moddev.mc.companion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.capabilities.Capabilities;

import zone.moddev.mc.lib.api.automation.AutomationInventory;
import zone.moddev.mc.lib.api.automation.ResourceGroup;
import zone.moddev.mc.lib.api.automation.ResourceType;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.api.capabilities.SideConfig;
import zone.moddev.mc.lib.machine.neoforge.BasePoweredMachine;
import zone.moddev.mc.lib.machine.neoforge.MachineInventoryLifecycle;

/** Refills each tick and supplies up to 1,000 FE per adjacent face per tick. */
public final class CreativePowerEntity extends BasePoweredMachine {
    private final ItemStorage empty = new ItemStorage(0);
    public final AutomationInventory automation;
    private final MachineInventoryLifecycle inventoryLifecycle;

    public CreativePowerEntity(BlockEntityType<?> type, BlockPos pos,
        BlockState state) {
        super(type, pos, state, 100_000, 0, 1_000);
        energy.setStored(energy.capacity());
        var group = new ResourceGroup<>("energy",
            Component.translatable("screen.mmdlib_companion.group.energy"),
            0xd85b5b, ResourceType.ENERGY, IoMode.OUTPUT, java.util.List.of(0),
            java.util.List.of("energy"), new SideConfig(IoMode.OUTPUT));
        automation = new zone.moddev.mc.lib.machine.neoforge.MachineResources(
            this, this::facing).energy(group, energy).build().automation();
        inventoryLifecycle = poweredInventory(automation);
        zone.moddev.mc.lib.gui.runtime.ScreenInventoryValidation.validate(this);
    }

    @Override
    protected void beforeMachineTick() {
        energy.insert(energy.space(), false);
    }

    @Override
    protected void afterMachineTick() {
        for (var side : Direction.values()) {
            if (!automation.mode(ResourceType.ENERGY, 0, side).canExtract())
                continue;
            var target = worldPosition.relative(side);
            if (!level.hasChunkAt(target))
                continue;
            var receiver = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                target, side.getOpposite());
            if (receiver != null && receiver.canReceive())
                energy.extract(receiver.receiveEnergy(1_000, false), false);
        }
    }

    @Override
    public ItemStorage screenItems() {
        return empty;
    }

    @Override
    public boolean screenAutomationAddons() {
        return false;
    }

    @Override
    protected MachineInventoryLifecycle machineInventory() {
        return inventoryLifecycle;
    }

    @Override
    protected void recoverResources(Level level, BlockPos pos,
        CompoundTag payload) {
        net.minecraft.world.Containers.dropItemStack(level, pos.getX() + .5,
            pos.getY() + .5, pos.getZ() + .5,
            RecoveryCapsuleItem.create(payload));
    }
}
