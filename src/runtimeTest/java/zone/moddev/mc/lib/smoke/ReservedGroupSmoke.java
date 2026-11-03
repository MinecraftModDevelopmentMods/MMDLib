package zone.moddev.mc.lib.smoke;

import java.util.List;

import com.mojang.serialization.JsonOps;

import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.api.capabilities.FluidStorage;
import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.api.capabilities.Role;
import zone.moddev.mc.lib.machine.ingredient.FluidAmount;
import zone.moddev.mc.lib.machine.inventory.FluidGroup;
import zone.moddev.mc.lib.machine.inventory.ItemGroup;
import zone.moddev.mc.lib.machine.inventory.MachineInventory;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;
import zone.moddev.mc.lib.machine.inventory.TankPlan;
import zone.moddev.mc.lib.machine.neoforge.FluidStacks;
import zone.moddev.mc.lib.machine.neoforge.StorageGroups;
import zone.moddev.mc.lib.machine.recipe.MatchResult;
import zone.moddev.mc.lib.machine.recipe.MatchResult.Reason;
import zone.moddev.mc.lib.machine.recipe.RecipeMatcher;

/** Checks that groups and the matcher see reservation plans, with the crusher recipe. */
final class ReservedGroupSmoke {
  private ReservedGroupSmoke() {}

  /** A crusher's storages: ore in slot 0, output in 1 and 2, byproduct in 3. */
  private static final class Crusher {
    final int[] changes = new int[1];
    final ItemStorage items =
        new ItemStorage(4)
            .onChange(() -> changes[0]++)
            .role(Role.INPUT, 0)
            .role(Role.OUTPUT, 1, 2, 3);
    final FluidStorage fluids = new FluidStorage().onChange(() -> changes[0]++);
    final EnergyBuffer energy =
        new EnergyBuffer(10_000_000_000L, 1_000).onChange(() -> changes[0]++);
    final MachineInventory inventory;

    Crusher(int ore, int water) {
      fluids.addTank(4_000, Role.INPUT);
      items.insertResult(0, new ItemStack(Items.COPPER_ORE, ore), false);
      fluids.tank(0).fill(new FluidStack(Fluids.WATER, water), FluidAction.EXECUTE);
      energy.setStored(20);
      inventory =
          MachineInventory.builder()
              .group("input", StorageGroups.items(items, 0))
              .group("output", StorageGroups.items(items, 1, 2))
              .group("byproduct", StorageGroups.items(items, 3))
              .group("water", StorageGroups.fluids(fluids, 0))
              .group("energy", StorageGroups.energy(energy))
              .build();
    }

    ItemGroup items(String key) {
      return ReservedGroupSmoke.items(inventory, key);
    }

    FluidGroup water() {
      return (FluidGroup) inventory.group("water").orElseThrow();
    }
  }

  static void check(ServerLevel level) {
    var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
    var crusher = MachineRecipeSmoke.recipe(ops, MachineRecipeSmoke.CRUSHER);
    var none = ItemStack.EMPTY;
    var water = FluidStacks.amount(new FluidStack(Fluids.WATER, 1_000));

    // Two lanes, inputs: what another plan takes is not there for this recipe.
    var lanes = new Crusher(1, 1_000);
    try (var tx = lanes.inventory.begin()) {
      require(lanes.items("input").reserve(0, new SlotPlan(1, none), tx), "reserve the ore");
      tx.commit();
    }
    expect(RecipeMatcher.check(crusher, lanes.inventory), Reason.MISSING_INPUT, "input");
    require(!RecipeMatcher.consume(crusher, lanes.inventory), "reserved ore is not consumed");
    require(lanes.items.getStackInSlot(0).getCount() == 1, "the reserved ore stays");
    var twoOres = new Crusher(2, 1_000);
    try (var tx = twoOres.inventory.begin()) {
      twoOres.items("input").reserve(0, new SlotPlan(1, none), tx);
      tx.commit();
    }
    require(RecipeMatcher.check(crusher, twoOres.inventory).ok(), "the second ore is free");
    var thirsty = new Crusher(3, 250);
    try (var tx = thirsty.inventory.begin()) {
      thirsty.water().reserve(0, new TankPlan(250, FluidAmount.EMPTY), tx);
      tx.commit();
    }
    expect(RecipeMatcher.check(crusher, thirsty.inventory), Reason.MISSING_INPUT, "water");

    // Two lanes, space: 63 copper are planned into the only empty output slot.
    var cramped = new Crusher(3, 1_000);
    cramped.items.setStackInSlot(1, new ItemStack(Items.COBBLESTONE, 64));
    require(RecipeMatcher.check(crusher, cramped.inventory).ok(), "room before the plan");
    try (var tx = cramped.inventory.begin()) {
      var copper = new ItemStack(Items.RAW_COPPER, 63);
      require(cramped.items("output").reserve(1, new SlotPlan(0, copper), tx), "reserve space");
      tx.commit();
    }
    expect(RecipeMatcher.check(crusher, cramped.inventory), Reason.NO_ROOM, "output");

    // Room comes from the end state: iron that is planned away is not there to be taken twice.
    var ironToGold =
        MachineRecipeSmoke.recipe(
            ops,
            """
            {"inputs":  {"main": [{"items": {"item": "minecraft:iron_ingot"}}]},
             "outputs": {"main": [{"item": {"id": "minecraft:gold_ingot"}}]}, "time": 10}
            """);
    var slot = new ItemStorage(1);
    var oneSlot = MachineInventory.builder().group("main", StorageGroups.items(slot, 0)).build();
    slot.setStackInSlot(0, iron(1));
    require(RecipeMatcher.check(ironToGold, oneSlot).ok(), "iron to gold without a plan");
    try (var tx = oneSlot.begin()) {
      require(items(oneSlot, "main").reserve(0, new SlotPlan(1, none), tx), "reserve the iron");
      tx.commit();
    }
    expect(RecipeMatcher.check(ironToGold, oneSlot), Reason.MISSING_INPUT, "main");

    // fits agrees with insertResult when the plan fills the slot.
    var topped = new ItemStorage(1);
    topped.setStackInSlot(0, iron(63));
    topped.setPlan(0, new SlotPlan(1, iron(2)));
    var toppedGroup = StorageGroups.items(topped, 0);
    require(!toppedGroup.fits(List.of(iron(1))), "fits refuses the last item");
    require(!topped.fits(0, iron(1)), "as insertResult does");

    // place reports the split, and null when it cannot all go.
    var pair = new ItemStorage(2);
    pair.setStackInSlot(0, iron(24));
    pair.setStackInSlot(1, iron(40));
    var group = StorageGroups.items(pair, 0, 1);
    var split = group.place(List.of(iron(64)), new long[2]);
    require(split != null && split[0][0] == 40 && split[0][1] == 24, "40 and 24");
    require(group.place(List.of(iron(65)), new long[2]) == null, "65 do not fit");
    require(group.place(List.of(iron(40), iron(24)), new long[2]) != null, "stacks add up");
    var freed = group.place(List.of(iron(88)), new long[] {24, 0});
    require(freed != null && freed[0][0] == 64 && freed[0][1] == 24, "taken items leave room");
    pair.setPlan(0, new SlotPlan(30, none));
    var skipped = group.place(List.of(iron(10)), new long[2]);
    require(skipped != null && skipped[0][0] == 0 && skipped[0][1] == 10, "skips an illegal plan");
    require(group.free(0) == 0 && group.free(1) == 40 && group.free(2) == 0, "free");

    var tanks = new FluidStorage();
    tanks.addTank(1_000, Role.STORAGE);
    tanks.addTank(1_000, Role.STORAGE);
    tanks.tank(0).setFluid(new FluidStack(Fluids.WATER, 400));
    var tankGroup = StorageGroups.fluids(tanks, 0, 1);
    var fluidSplit = tankGroup.place(List.of(water.withAmount(1_200)), new long[2]);
    require(fluidSplit != null && fluidSplit[0][0] == 600 && fluidSplit[0][1] == 600, "mB split");
    require(tankGroup.place(List.of(water.withAmount(1_700)), new long[2]) == null, "too much");
    tanks.setPlan(0, new TankPlan(0, water.withAmount(300)));
    var reservedSplit = tankGroup.place(List.of(water.withAmount(1_000)), new long[2]);
    require(
        reservedSplit != null && reservedSplit[0][0] == 300 && reservedSplit[0][1] == 700,
        "tank plans leave less room");
    tanks.setPlan(0, new TankPlan(100, FluidAmount.EMPTY));
    require(tankGroup.free(0) == 300 && tankGroup.free(1) == 0, "free mB");

    // Undo: a plan is undone with the transaction, and contents and plan restore together.
    var undo = new Crusher(5, 1_000);
    try (var tx = undo.inventory.begin()) {
      require(undo.items("input").extract(0, 2, tx) == 2, "extract");
      require(undo.items("input").reserve(0, new SlotPlan(1, none), tx), "reserve");
    }
    require(undo.items.plan(0).isNone(), "plan undone");
    require(undo.items.getStackInSlot(0).getCount() == 5, "contents undone");
    try (var tx = undo.inventory.begin()) {
      undo.items("input").reserve(0, new SlotPlan(1, none), tx);
      tx.commit();
    }
    require(undo.items.plan(0).sameAs(new SlotPlan(1, none)), "committed plan stays");
    try (var tx = undo.inventory.begin()) {
      var input = undo.items("input");
      input.release(0, new SlotPlan(1, none), tx);
      require(undo.items.plan(0).isNone(), "released");
      input.restore(0, new SlotPlan(2, none), tx);
      require(undo.items.plan(0).sameAs(new SlotPlan(2, none)), "restored past the check");
    }
    require(undo.items.plan(0).sameAs(new SlotPlan(1, none)), "release and restore undone");

    // Single-slot insert puts nothing anywhere else.
    var target = new Crusher(0, 0);
    try (var tx = target.inventory.begin()) {
      var copper = new ItemStack(Items.RAW_COPPER, 3);
      require(target.items("output").insert(1, copper, tx) == 3, "inserted");
      require(target.items("output").insert(5, copper, tx) == 0, "out of range");
      tx.commit();
    }
    require(target.items.getStackInSlot(1).isEmpty(), "the other slot stays empty");
    require(target.items.getStackInSlot(2).getCount() == 3, "group slot 1 holds 3");

    // consume and produce cannot touch what is reserved, and still work on the rest.
    var work = new Crusher(3, 1_000);
    try (var tx = work.inventory.begin()) {
      work.items("input").reserve(0, new SlotPlan(1, none), tx);
      work.items("output").reserve(0, new SlotPlan(0, new ItemStack(Items.GOLD_INGOT)), tx);
      tx.commit();
    }
    require(RecipeMatcher.consume(crusher, work.inventory), "consume the free ore");
    require(RecipeMatcher.consume(crusher, work.inventory), "and the next");
    require(!RecipeMatcher.consume(crusher, work.inventory), "but not the reserved one");
    require(work.items.getStackInSlot(0).getCount() == 1, "one ore left");
    require(RecipeMatcher.produce(crusher, work.inventory, missing()), "produce");
    require(work.items.getStackInSlot(1).isEmpty(), "the reserved slot got nothing");
    require(work.items.getStackInSlot(2).getCount() == 2, "the free slot got the copper");

    // Checking, placing and counting free never run a change callback.
    var quiet = new Crusher(3, 1_000);
    int before = quiet.changes[0];
    RecipeMatcher.check(crusher, quiet.inventory);
    quiet.items("output").place(List.of(new ItemStack(Items.RAW_COPPER, 2)), new long[2]);
    quiet.items("output").free(0);
    quiet.water().place(List.of(water.withAmount(100)), new long[1]);
    quiet.water().free(0);
    require(quiet.changes[0] == before, "no change callbacks");
  }

  private static ItemGroup items(MachineInventory inventory, String key) {
    return (ItemGroup) inventory.group(key).orElseThrow();
  }

  /** A random that misses every chance. */
  private static RandomSource missing() {
    return new LegacyRandomSource(0) {
      @Override
      public float nextFloat() {
        return .99F;
      }
    };
  }

  private static ItemStack iron(int count) {
    return new ItemStack(Items.IRON_INGOT, count);
  }

  private static void expect(MatchResult result, Reason reason, String group) {
    require(
        result.reason() == reason && group.equals(result.group()),
        "expected " + reason + " " + group + ", got " + result);
  }

  private static void require(boolean condition, String what) {
    if (!condition) throw new AssertionError("Reserved group smoke failed: " + what);
  }
}
