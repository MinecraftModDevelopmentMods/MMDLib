package zone.moddev.mc.lib.smoke;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.api.capabilities.FluidStorage;
import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.api.capabilities.Role;
import zone.moddev.mc.lib.machine.feature.FeatureHost;
import zone.moddev.mc.lib.machine.feature.RecipeProcessor;
import zone.moddev.mc.lib.machine.inventory.MachineInventory;
import zone.moddev.mc.lib.machine.neoforge.StorageGroups;

/**
 * A machine's storages, inventory, processor and feature host for the processor smoke test. {@link
 * #changes} counts every change the host or a storage reports.
 */
final class ProcessorRig {
  final int[] changes = new int[1];
  final ItemStorage items;
  final FluidStorage fluids = new FluidStorage().onChange(() -> changes[0]++);
  final EnergyBuffer energy = new EnergyBuffer(10_000_000_000L, 1_000).onChange(() -> changes[0]++);
  final MachineInventory inventory;
  final FeatureHost host = new FeatureHost(() -> changes[0]++);
  final RecipeProcessor processor;

  private ProcessorRig(ItemStorage storage, boolean crusher, boolean withEnergy) {
    items = storage.onChange(() -> changes[0]++);
    var builder = MachineInventory.builder();
    if (crusher) {
      fluids.addTank(4_000, Role.INPUT);
      builder
          .group("input", StorageGroups.items(items, 0))
          .group("output", StorageGroups.items(items, 1, 2))
          .group("byproduct", StorageGroups.items(items, 3))
          .group("water", StorageGroups.fluids(fluids, 0));
    } else {
      builder.group("slot", StorageGroups.items(items, 0));
    }
    if (withEnergy) builder.group("energy", StorageGroups.energy(energy));
    inventory = builder.build();
    processor = new RecipeProcessor("work", MachineRecipeTypeSmoke.REFINING, inventory);
    host.add(processor);
  }

  /** The crusher of the worked run: ore in slot 0, output in 1 and 2, byproduct in 3. */
  static ProcessorRig crusher(int ore, int water, long fe) {
    var storage = new ItemStorage(4).role(Role.INPUT, 0).role(Role.OUTPUT, 1, 2, 3);
    var rig = new ProcessorRig(storage, true, true);
    rig.items.insertResult(0, new ItemStack(Items.COPPER_ORE, ore), false);
    rig.fluids.tank(0).fill(new FluidStack(Fluids.WATER, water), FluidAction.EXECUTE);
    rig.energy.setStored(fe);
    rig.changes[0] = 0;
    return rig;
  }

  /** One slot that is both the input and the output, with or without an energy buffer. */
  static ProcessorRig oneSlot(boolean withEnergy) {
    var rig = new ProcessorRig(new ItemStorage(1), false, withEnergy);
    rig.energy.setStored(1_000_000);
    rig.changes[0] = 0;
    return rig;
  }

  /** A second processor on the same host and storages. */
  RecipeProcessor lane(String key) {
    var lane = new RecipeProcessor(key, MachineRecipeTypeSmoke.REFINING, inventory);
    host.add(lane);
    return lane;
  }

  void ticks(ServerLevel level, int count) {
    for (int i = 0; i < count; i++) host.tick(level);
  }
}
