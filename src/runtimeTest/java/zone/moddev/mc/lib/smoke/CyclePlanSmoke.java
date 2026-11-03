package zone.moddev.mc.lib.smoke;

import java.util.List;
import java.util.Map;

import com.mojang.serialization.JsonOps;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
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
import zone.moddev.mc.lib.machine.inventory.MachineInventory;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;
import zone.moddev.mc.lib.machine.neoforge.StorageGroups;
import zone.moddev.mc.lib.machine.recipe.CyclePlan;
import zone.moddev.mc.lib.machine.recipe.CyclePlan.At;
import zone.moddev.mc.lib.machine.recipe.CyclePlan.FluidTake;
import zone.moddev.mc.lib.machine.recipe.CyclePlan.ItemTake;
import zone.moddev.mc.lib.machine.recipe.CyclePlan.Placed;
import zone.moddev.mc.lib.machine.recipe.MachineProduct.ItemProduct;

/** Checks one cycle's plan end to end with the crusher recipe: build, reserve, restore, finish. */
final class CyclePlanSmoke {
  private static final ResourceLocation ID = ResourceLocation.parse("example:crushing/copper");

  private CyclePlanSmoke() {}

  /** A crusher's storages: ore in slot 0, output in 1 and 2, byproduct in 3. */
  private static final class Crusher {
    final ItemStorage items = new ItemStorage(4).role(Role.INPUT, 0).role(Role.OUTPUT, 1, 2, 3);
    final FluidStorage fluids = new FluidStorage();
    final EnergyBuffer energy = new EnergyBuffer(10_000_000_000L, 1_000);
    final MachineInventory inventory;

    Crusher(int ore, int water) {
      fluids.addTank(4_000, Role.INPUT);
      items.insertResult(0, new ItemStack(Items.COPPER_ORE, ore), false);
      fluids.tank(0).fill(new FluidStack(Fluids.WATER, water), FluidAction.EXECUTE);
      inventory =
          MachineInventory.builder()
              .group("input", StorageGroups.items(items, 0))
              .group("output", StorageGroups.items(items, 1, 2))
              .group("byproduct", StorageGroups.items(items, 3))
              .group("water", StorageGroups.fluids(fluids, 0))
              .group("energy", StorageGroups.energy(energy))
              .build();
    }

    boolean unplanned() {
      for (int slot = 0; slot < 4; slot++) if (!items.plan(slot).isNone()) return false;
      return fluids.plan(0).isNone();
    }
  }

  static void check(ServerLevel level) {
    var access = level.registryAccess();
    var jsonOps = RegistryOps.create(JsonOps.INSTANCE, access);
    var crusher = MachineRecipeSmoke.recipe(jsonOps, MachineRecipeSmoke.CRUSHER);
    var none = ItemStack.EMPTY;
    var rawCopper = new ItemStack(Items.RAW_COPPER, 2);

    // of: what the crusher would reserve, with no energy stored.
    var start = new Crusher(3, 1_000);
    var plan = CyclePlan.of(ID, crusher, start.inventory).orElseThrow();
    require(plan.recipe().equals(ID) && plan.time() == 200, "recipe and time");
    require(plan.items().size() == 1, "one item take");
    var ore = plan.items().getFirst();
    require(
        ore.group().equals("input")
            && ore.slot() == 0
            && ItemStack.matches(ore.stack(), new ItemStack(Items.COPPER_ORE)),
        "takes 1 ore from slot 0");
    require(plan.fluids().size() == 1, "one fluid take");
    var water = plan.fluids().getFirst();
    require(
        water.group().equals("water") && water.tank() == 0 && water.fluid().amount() == 250,
        "takes 250 mB from tank 0");
    require(plan.products().size() == 2, "two products");
    var copper = plan.products().getFirst();
    require(
        copper.group().equals("output")
            && copper.product() instanceof ItemProduct item
            && ItemStack.matches(item.stack(), rawCopper)
            && copper.at().equals(List.of(new At(0, 2))),
        "2 raw copper at group slot 0");
    require(plan.energy().equals(Map.of("energy", 4_000L)), "energy " + plan.energy());
    require(start.unplanned(), "of changes nothing");
    require(
        CyclePlan.of(ID, crusher, new Crusher(0, 1_000).inventory).isEmpty(), "no ore, no plan");
    require(
        CyclePlan.of(ID, crusher, new Crusher(3, 249).inventory).isEmpty(), "too little water");

    // reserve: the ore is locked and the space is held; a second one that cannot fit is refused.
    var held = new Crusher(3, 1_000);
    var first = CyclePlan.of(ID, crusher, held.inventory).orElseThrow();
    require(first.reserve(held.inventory), "reserve");
    require(held.items.freeToTake(0) == 2, "the ore is locked");
    require(held.fluids.plan(0).take() == 250, "the water is locked");
    require(held.items.plan(1).sameAs(new SlotPlan(0, rawCopper)), "the copper space is held");
    require(
        held.items.plan(3).sameAs(new SlotPlan(0, new ItemStack(Items.GRAVEL))),
        "the gravel space is held");
    var cramped = new Crusher(3, 1_000);
    cramped.items.setStackInSlot(1, new ItemStack(Items.RAW_COPPER, 62));
    cramped.items.setStackInSlot(2, new ItemStack(Items.COBBLESTONE, 64));
    var one = CyclePlan.of(ID, crusher, cramped.inventory).orElseThrow();
    var two = CyclePlan.of(ID, crusher, cramped.inventory).orElseThrow();
    require(one.reserve(cramped.inventory), "the first fits");
    require(!two.reserve(cramped.inventory), "the second would overfill the output");
    require(
        cramped.items.plan(0).sameAs(new SlotPlan(1, none))
            && cramped.items.plan(1).sameAs(new SlotPlan(0, rawCopper))
            && cramped.fluids.plan(0).take() == 250,
        "the first stays as it was");

    // finish: inputs taken, products in, every plan gone.
    var run = new Crusher(3, 1_000);
    var cycle = CyclePlan.of(ID, crusher, run.inventory).orElseThrow();
    require(cycle.reserve(run.inventory), "reserve before finishing");
    require(cycle.finish(run.inventory, fixed(.99F)), "finish");
    require(run.items.getStackInSlot(0).getCount() == 2, "ore 3 to 2");
    require(run.fluids.tank(0).getFluidAmount() == 750, "water down 250");
    require(ItemStack.matches(run.items.getStackInSlot(1), rawCopper), "2 raw copper out");
    require(run.unplanned(), "every plan is gone");

    // A hit puts the byproduct in; a miss leaves its space free and no plan behind.
    var hit = new Crusher(3, 1_000);
    var hitPlan = CyclePlan.of(ID, crusher, hit.inventory).orElseThrow();
    hitPlan.reserve(hit.inventory);
    require(hitPlan.finish(hit.inventory, fixed(0F)), "finish on a hit");
    require(hit.items.getStackInSlot(3).is(Items.GRAVEL), "gravel on a hit");
    var miss = new Crusher(3, 1_000);
    var missPlan = CyclePlan.of(ID, crusher, miss.inventory).orElseThrow();
    missPlan.reserve(miss.inventory);
    require(missPlan.finish(miss.inventory, fixed(.5F)), "finish on a miss");
    require(miss.items.getStackInSlot(3).isEmpty(), "no gravel on a miss");
    require(miss.items.plan(3).isNone() && miss.unplanned(), "the byproduct slot is free");

    // The one-slot machine: its input slot is also its output slot.
    var ironToGold =
        MachineRecipeSmoke.recipe(
            jsonOps,
            """
            {"inputs":  {"main": [{"items": {"item": "minecraft:iron_ingot"}}]},
             "outputs": {"main": [{"item": {"id": "minecraft:gold_ingot"}}]}, "time": 10}
            """);
    var slot = new ItemStorage(1);
    var oneSlot = MachineInventory.builder().group("main", StorageGroups.items(slot, 0)).build();
    slot.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
    var swap = CyclePlan.of(ID, ironToGold, oneSlot).orElseThrow();
    require(swap.reserve(oneSlot), "reserve iron to gold");
    require(slot.plan(0).sameAs(new SlotPlan(1, new ItemStack(Items.GOLD_INGOT))), "one plan");
    require(swap.finish(oneSlot, fixed(0F)), "finish iron to gold");
    require(
        ItemStack.matches(slot.getStackInSlot(0), new ItemStack(Items.GOLD_INGOT))
            && slot.plan(0).isNone(),
        "1 gold is left");

    // A failed finish changes nothing: the plan stays and nothing is inserted.
    var emptied = new Crusher(3, 1_000);
    var emptiedPlan = CyclePlan.of(ID, crusher, emptied.inventory).orElseThrow();
    emptiedPlan.reserve(emptied.inventory);
    emptied.items.setStackInSlot(0, none);
    require(!emptiedPlan.finish(emptied.inventory, fixed(0F)), "no ore, no finish");
    require(emptied.fluids.tank(0).getFluidAmount() == 1_000, "water stays");
    require(emptied.items.getStackInSlot(1).isEmpty(), "nothing inserted");
    require(
        emptied.items.plan(0).sameAs(new SlotPlan(1, none))
            && emptied.items.plan(1).sameAs(new SlotPlan(0, rawCopper))
            && emptied.fluids.plan(0).take() == 250,
        "the plan stays in place");
    var dirt = new Crusher(3, 1_000);
    var dirtPlan = CyclePlan.of(ID, crusher, dirt.inventory).orElseThrow();
    dirtPlan.reserve(dirt.inventory);
    dirt.items.setStackInSlot(0, new ItemStack(Items.DIRT, 3));
    require(!dirtPlan.finish(dirt.inventory, fixed(0F)), "dirt is not ore");
    require(
        ItemStack.matches(dirt.items.getStackInSlot(0), new ItemStack(Items.DIRT, 3)),
        "the dirt is untouched");
    require(dirt.items.getStackInSlot(1).isEmpty(), "nothing inserted");
    require(dirt.items.plan(0).sameAs(new SlotPlan(1, none)), "the plan stays on the slot");

    // Only a hit needs its space: a byproduct slot filled mid-cycle blocks a hit, not a miss.
    var jammed = new Crusher(3, 1_000);
    var jammedPlan = CyclePlan.of(ID, crusher, jammed.inventory).orElseThrow();
    jammedPlan.reserve(jammed.inventory);
    jammed.items.setStackInSlot(3, new ItemStack(Items.COBBLESTONE, 64));
    require(!jammedPlan.finish(jammed.inventory, fixed(0F)), "a hit has no room");
    require(
        jammed.items.getStackInSlot(0).getCount() == 3
            && jammed.items.getStackInSlot(1).isEmpty()
            && jammed.items.plan(0).sameAs(new SlotPlan(1, none)),
        "a blocked finish changes nothing");
    require(jammedPlan.finish(jammed.inventory, fixed(.5F)), "a miss needs no room");
    require(jammed.items.getStackInSlot(3).getCount() == 64, "the cobblestone stays");
    require(ItemStack.matches(jammed.items.getStackInSlot(1), rawCopper), "the dust is made");

    // release: this plan goes, another lane's plan on the same slot stays.
    var shared = new Crusher(3, 1_000);
    var other = new SlotPlan(1, none);
    shared.items.setPlan(0, other);
    var mine = CyclePlan.of(ID, crusher, shared.inventory).orElseThrow();
    mine.reserve(shared.inventory);
    mine.release(shared.inventory);
    require(shared.items.plan(0).sameAs(other), "the other plan stays");
    require(shared.items.plan(1).isNone() && shared.items.plan(3).isNone(), "outputs released");
    require(shared.fluids.plan(0).isNone(), "tank released");
    var alone = new Crusher(3, 1_000);
    var lone = CyclePlan.of(ID, crusher, alone.inventory).orElseThrow();
    lone.reserve(alone.inventory);
    lone.release(alone.inventory);
    require(alone.unplanned(), "all plans are NONE");

    // Round trip: the same plan comes back from NBT and restores the same reservations.
    var nbt = access.createSerializationContext(NbtOps.INSTANCE);
    var saved = new Crusher(3, 1_000);
    var savedPlan = CyclePlan.of(ID, crusher, saved.inventory).orElseThrow();
    savedPlan.reserve(saved.inventory);
    var tag = (CompoundTag) CyclePlan.CODEC.encodeStart(nbt, savedPlan).getOrThrow();
    var loaded = CyclePlan.CODEC.parse(nbt, tag).getOrThrow();
    require(
        loaded.recipe().equals(ID)
            && loaded.time() == 200
            && loaded.energy().equals(savedPlan.energy())
            && loaded.products().size() == 2,
        "decoded plan");
    var fresh = new Crusher(3, 1_000);
    require(loaded.restore(fresh.inventory), "restore on fresh storages");
    for (int index = 0; index < 4; index++)
      require(fresh.items.plan(index).sameAs(saved.items.plan(index)), "plan " + index);
    require(fresh.fluids.plan(0).sameAs(saved.fluids.plan(0)), "tank plan");

    // restore refuses what no longer holds, and leaves no plan behind.
    var oreTake = new ItemStack(Items.COPPER_ORE);
    var cases =
        Map.of(
            "slot past the end", withItems(savedPlan, List.of(new ItemTake("input", 1, oreTake))),
            "missing group", withItems(savedPlan, List.of(new ItemTake("nothing", 0, oreTake))),
            "fewer items",
                withItems(
                    savedPlan,
                    List.of(new ItemTake("input", 0, new ItemStack(Items.COPPER_ORE, 4)))),
            "different item",
                withItems(
                    savedPlan, List.of(new ItemTake("input", 0, new ItemStack(Items.IRON_ORE)))),
            "product past the end",
                new CyclePlan(
                    ID,
                    200,
                    savedPlan.items(),
                    savedPlan.fluids(),
                    List.of(new Placed("output", copper.product(), List.of(new At(9, 2)))),
                    Map.of()),
            "fewer mB",
                new CyclePlan(
                    ID,
                    200,
                    savedPlan.items(),
                    List.of(new FluidTake("water", 0, water.fluid().withAmount(2_000))),
                    savedPlan.products(),
                    Map.of()));
    for (var entry : cases.entrySet()) {
      var target = new Crusher(3, 1_000);
      require(!entry.getValue().restore(target.inventory), entry.getKey() + " is refused");
      require(target.unplanned(), entry.getKey() + " leaves no plan");
    }

    // An unknown item is a decode error, not an exception.
    var broken = tag.copy();
    broken
        .getList("items", 10)
        .getCompound(0)
        .getCompound("stack")
        .putString("id", "missing_mod:thing");
    require(CyclePlan.CODEC.parse(nbt, broken).error().isPresent(), "unknown item errors");
  }

  private static CyclePlan withItems(CyclePlan plan, List<ItemTake> items) {
    return new CyclePlan(
        plan.recipe(), plan.time(), items, plan.fluids(), plan.products(), plan.energy());
  }

  /** A random whose nextFloat() is always {@code value}: 0 always hits a chance, 0.99 misses. */
  private static RandomSource fixed(float value) {
    return new LegacyRandomSource(0) {
      @Override
      public float nextFloat() {
        return value;
      }
    };
  }

  private static void require(boolean condition, String what) {
    if (!condition) throw new AssertionError("Cycle plan smoke failed: " + what);
  }
}
