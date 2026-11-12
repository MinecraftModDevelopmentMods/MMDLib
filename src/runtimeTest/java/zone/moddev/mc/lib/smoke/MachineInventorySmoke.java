package zone.moddev.mc.lib.smoke;

import java.util.List;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.api.capabilities.FluidStorage;
import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.api.capabilities.Role;
import zone.moddev.mc.lib.machine.ingredient.FluidAmount;
import zone.moddev.mc.lib.machine.inventory.EnergyGroup;
import zone.moddev.mc.lib.machine.inventory.FluidGroup;
import zone.moddev.mc.lib.machine.inventory.ItemGroup;
import zone.moddev.mc.lib.machine.inventory.MachineInventory;
import zone.moddev.mc.lib.machine.neoforge.StorageGroups;

/** Checks slot groups over MMDLib's storages and snapshot transactions. */
final class MachineInventorySmoke {
    private MachineInventorySmoke() {
    }

    static void check(ServerLevel level) {
        var changes = new int[1];
        var items = new ItemStorage(4).onChange(() -> changes[0]++)
            .role(Role.INPUT, 0).role(Role.OUTPUT, 1, 2, 3);
        var fluids = new FluidStorage().onChange(() -> changes[0]++);
        fluids.addTank(4_000, Role.INPUT);
        var energy = new EnergyBuffer(10_000_000_000L, 100)
            .onChange(() -> changes[0]++);
        var inventory = MachineInventory.builder()
            .group("input", StorageGroups.items(items, 0))
            .group("output", StorageGroups.items(items, 1, 2))
            .group("byproduct", StorageGroups.items(items, 3))
            .group("water", StorageGroups.fluids(fluids, 0))
            .group("energy", StorageGroups.energy(energy)).build();
        var input = (ItemGroup) inventory.group("input").orElseThrow();
        var output = (ItemGroup) inventory.group("output").orElseThrow();
        var byproduct = (ItemGroup) inventory.group("byproduct").orElseThrow();
        var water = (FluidGroup) inventory.group("water").orElseThrow();
        var power = (EnergyGroup) inventory.group("energy").orElseThrow();
        require(
            inventory.keys().size() == 5 && inventory.group("steam").isEmpty(),
            "keys");
        require(output.size() == 2 && water.tanks() == 1, "group sizes");

        // The machine inserts into OUTPUT slots, which automation can't.
        try (var tx = inventory.begin()) {
            require(
                output.insert(new ItemStack(Items.RAW_COPPER, 70), tx) == 70,
                "owner bypasses roles");
            tx.commit();
        }
        require(items.getStackInSlot(1).getCount() == 64
            && items.getStackInSlot(2).getCount() == 6, "spread");

        // Without commit, everything the tx touched goes back.
        try (var tx = inventory.begin()) {
            require(output.insert(new ItemStack(Items.RAW_COPPER, 5), tx) == 5,
                "insert in tx");
            require(water.insert(water(1_000), tx) == 1_000, "fill in tx");
        }
        require(items.getStackInSlot(2).getCount() == 6
            && fluids.getFluidInTank(0).isEmpty(), "rolled back");

        try (var tx = inventory.begin()) {
            require(water.insert(water(1_000), tx) == 1_000, "fill");
            tx.commit();
        }
        require(water.peek(0).amount() == 1_000
            && water.peek(0).sameFluid(water(1)), "committed fill");
        try (var tx = inventory.begin()) {
            require(water.extract(0, 250, tx) == 250, "drain");
            require(
                water
                    .insert(new FluidAmount(Fluids.LAVA.builtInRegistryHolder(),
                        DataComponentPatch.EMPTY, 10), tx) == 0,
                "other fluid refused");
        }
        require(water.peek(0).amount() == 1_000, "drain rolled back");

        // Energy above 2^31 and above the buffer's transfer rate of 100.
        try (var tx = inventory.begin()) {
            require(power.insert(5_000_000_000L, tx) == 5_000_000_000L,
                "big insert");
            tx.commit();
        }
        require(energy.stored() == 5_000_000_000L, "big energy stored");
        try (var tx = inventory.begin()) {
            require(power.extract(4_000_000_000L, tx) == 4_000_000_000L,
                "big extract");
            require(power.insert(9_000_000_000L, tx) == 9_000_000_000L,
                "insert up to capacity");
            require(power.insert(1, tx) == 0, "full");
        }
        require(power.stored() == 5_000_000_000L
            && power.capacity() == 10_000_000_000L, "energy rolled back");

        // fits() works on copies and never touches the storages.
        items.insertResult(3, new ItemStack(Items.COBBLESTONE, 64), false);
        int before = changes[0];
        require(!byproduct.fits(List.of(new ItemStack(Items.GRAVEL))),
            "full slot");
        require(byproduct.fits(List.of()), "nothing always fits");
        require(output.fits(List.of(new ItemStack(Items.RAW_COPPER, 58))),
            "room left");
        require(!output.fits(List.of(new ItemStack(Items.RAW_COPPER, 59))),
            "one too many");
        require(!output.fits(List.of(new ItemStack(Items.RAW_COPPER, 50),
            new ItemStack(Items.GRAVEL))), "no free slot");
        require(water.fits(List.of(water(3_000)))
            && !water.fits(List.of(water(3_001))), "tank room");
        require(changes[0] == before, "fits changed nothing");

        // The machine may take from INPUT slots; bad indexes take nothing.
        items.insertResult(0, new ItemStack(Items.COPPER_ORE, 3), false);
        try (var tx = inventory.begin()) {
            require(input.extract(0, 2, tx) == 2, "owner takes input");
            require(
                input.extract(9, 1, tx) == 0 && input.extract(-1, 1, tx) == 0,
                "out of range");
            require(water.extract(3, 10, tx) == 0, "no such tank");
            tx.commit();
        }
        require(items.getStackInSlot(0).getCount() == 1, "input taken");
        input.peek(0).setCount(50);
        require(items.getStackInSlot(0).getCount() == 1, "peek is a copy");

        var closed = inventory.begin();
        closed.close();
        expectState(
            () -> output.insert(new ItemStack(Items.RAW_COPPER), closed),
            "insert after close");
        expectState(closed::commit, "commit after close");
        var committed = inventory.begin();
        committed.commit();
        committed.close();
        expectState(() -> power.extract(1, committed), "extract after commit");
        try {
            MachineInventory.builder().group("a", input).group("a", output);
            throw new AssertionError(
                "Machine inventory smoke failed: duplicate key accepted");
        } catch (IllegalArgumentException expected) {
        }
    }

    private static FluidAmount water(int amount) {
        return new FluidAmount(Fluids.WATER.builtInRegistryHolder(),
            DataComponentPatch.EMPTY, amount);
    }

    private static void expectState(Runnable action, String what) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Machine inventory smoke failed: " + what);
    }

    private static void require(boolean condition, String what) {
        if (!condition)
            throw new AssertionError("Machine inventory smoke failed: " + what);
    }
}
