package zone.moddev.mc.lib.smoke;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import com.mojang.serialization.JsonOps;

import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;

import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.machine.feature.RecipeProcessor.State;
import zone.moddev.mc.lib.machine.feature.RecipeProcessor.Status;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;
import zone.moddev.mc.lib.machine.recipe.CyclePlan;
import zone.moddev.mc.lib.machine.recipe.CyclePlan.At;
import zone.moddev.mc.lib.machine.recipe.CyclePlan.ItemTake;
import zone.moddev.mc.lib.machine.recipe.CyclePlan.Placed;
import zone.moddev.mc.lib.machine.recipe.MachineProduct.ItemProduct;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeBuilder;
import zone.moddev.mc.lib.machine.recipe.MachineRecipeEntry;

/** Runs recipe processors tick by tick: the worked run, jams, lanes, chances and removed inputs. */
final class ProcessorSmoke {
  private ProcessorSmoke() {}

  /** The crusher's worked run, with inputs reserved rather than taken at the start. */
  static void workedRun(ServerLevel level) {
    var rig = ProcessorRig.crusher(3, 1_000, 3_000);

    rig.ticks(level, 1);
    expect(rig, Status.RUNNING, 0, "tick 0");
    require(rig.energy.stored() == 3_000, "the starting tick draws nothing");
    require(rig.items.getStackInSlot(0).getCount() == 3, "ore stays in the slot");
    require(rig.items.plan(0).take() == 1, "one ore reserved");
    require(rig.fluids.plan(0).take() == 250, "250 mB reserved");
    require(rig.processor.time() == 200, "time " + rig.processor.time());

    rig.ticks(level, 1);
    expect(rig, Status.RUNNING, 1, "tick 1");
    require(rig.energy.stored() == 2_980, "tick 1 energy " + rig.energy.stored());

    rig.ticks(level, 149);
    expect(rig, Status.RUNNING, 150, "tick 150");
    require(rig.energy.stored() == 0, "tick 150 energy " + rig.energy.stored());

    for (int tick = 151; tick <= 160; tick++) {
      rig.ticks(level, 1);
      expect(rig, Status.STALLED, 150, "tick " + tick);
      require(rig.items.plan(0).take() == 1 && rig.fluids.plan(0).take() == 250, "inputs held");
      require(!rig.items.plan(1).isNone(), "room held");
    }

    for (int tick = 161; tick <= 209; tick++) {
      rig.energy.setStored(rig.energy.stored() + 200);
      rig.ticks(level, 1);
      expect(rig, Status.RUNNING, tick - 10, "tick " + tick);
      if (tick == 161) require(rig.energy.stored() == 180, "tick 161 energy");
    }

    rig.energy.setStored(rig.energy.stored() + 200);
    rig.ticks(level, 1);
    require(rig.processor.status() == Status.IDLE, "tick 210 " + rig.processor.status());
    require(rig.processor.time() == 0, "idle has no time");
    require(rig.energy.stored() == 9_000, "tick 210 energy " + rig.energy.stored());
    require(rig.items.getStackInSlot(0).getCount() == 2, "ore taken at the end");
    require(rig.fluids.getFluidInTank(0).getAmount() == 750, "water taken at the end");
    require(rawCopper(rig) == 2, "two raw copper");
    require(unplanned(rig), "plans released");

    rig.energy.setStored(rig.energy.stored() + 200);
    rig.ticks(level, 1);
    expect(rig, Status.RUNNING, 0, "tick 211");
    require(rig.energy.stored() == 9_200, "tick 211 energy " + rig.energy.stored());
    require(rig.items.plan(0).take() == 1, "the next ore is reserved");
  }

  /** A hopper pushing iron every tick can't use up the slot that is reserved for the gold. */
  static void hopperCantJam(ServerLevel level) {
    var rig = ProcessorRig.oneSlot(true);
    var hopper = rig.items.view(IoMode.BOTH);
    rig.items.insertResult(0, new ItemStack(Items.IRON_INGOT), false);
    int pushed = 0;
    int refused = 0;
    for (int tick = 0; tick < 30; tick++) {
      rig.ticks(level, 1);
      pushed++;
      if (!hopper.insertItem(0, new ItemStack(Items.IRON_INGOT), false).isEmpty()) refused++;
    }
    require(refused == pushed, "the hopper was refused throughout: " + refused + "/" + pushed);
    var held = rig.items.getStackInSlot(0);
    require(held.is(Items.GOLD_INGOT) && held.getCount() == 1, "one gold, got " + held);
    require(rig.processor.status() == Status.IDLE, "idle");
  }

  /** Two processors sharing storages never run on the same ore or the same room. */
  static void twoLanes(ServerLevel level) {
    var rig = ProcessorRig.crusher(1, 1_000, 10_000);
    var other = rig.lane("other");
    rig.ticks(level, 1);
    require(rig.processor.status() == Status.RUNNING, "first lane runs");
    require(other.status() == Status.IDLE, "second lane has no ore");
    rig.ticks(level, 300);
    require(rawCopper(rig) == 2, "one set of products, got " + rawCopper(rig));

    rig = ProcessorRig.crusher(3, 1_000, 100_000);
    other = rig.lane("other");
    rig.items.insertResult(1, new ItemStack(Items.RAW_COPPER, 62), false);
    rig.items.insertResult(2, new ItemStack(Items.COBBLESTONE, 64), false);
    rig.ticks(level, 1);
    require(rig.processor.status() == Status.RUNNING, "first lane runs with room");
    require(other.status() == Status.IDLE, "second lane has no room");
    rig.ticks(level, 190);
    require(other.status() == Status.IDLE, "second lane still waits");
    rig.ticks(level, 20);
    require(rawCopper(rig) == 64, "only one set fit, got " + rawCopper(rig));
  }

  /** The gravel byproduct's space is only held for the cycle: a miss leaves it free. */
  static void chanceKeepsNothing(ServerLevel level) {
    var rig = ProcessorRig.crusher(16, 4_000, 1_000_000);
    boolean missed = false;
    for (int cycle = 0; cycle < 16; cycle++) {
      int gravel = rig.items.getStackInSlot(3).getCount();
      var rest = new ItemStack(Items.GRAVEL, 64 - gravel);
      rig.ticks(level, 1);
      require(rig.processor.status() == Status.RUNNING, "cycle " + cycle + " started");
      require(!rig.items.fits(3, rest), "gravel's space is held");
      rig.energy.setStored(1_000_000);
      rig.ticks(level, 200);
      require(rig.processor.status() == Status.IDLE, "cycle " + cycle + " finished");
      require(unplanned(rig), "plans released");
      int after = rig.items.getStackInSlot(3).getCount();
      require(rig.items.fits(3, new ItemStack(Items.GRAVEL, 64 - after)), "space is free again");
      missed |= after == gravel;
    }
    require(missed, "16 cycles at 25% never missed");
  }

  /** An input taken out mid-cycle blocks the finish and undoes nothing else. */
  static void inputRemoved(ServerLevel level) {
    var rig = ProcessorRig.crusher(1, 1_000, 10_000);
    rig.ticks(level, 101);
    var ore = rig.items.getStackInSlot(0);
    rig.items.setStackInSlot(0, ItemStack.EMPTY);
    rig.ticks(level, 100);
    require(rig.processor.progress() == 200, "progress " + rig.processor.progress());
    require(rig.processor.status() == Status.BLOCKED, "blocked " + rig.processor.status());
    long energy = rig.energy.stored();
    int changes = rig.changes[0];
    rig.ticks(level, 5);
    require(rig.changes[0] == changes, "a blocked machine marked changed");
    require(rig.processor.status() == Status.BLOCKED, "still blocked");
    require(rig.processor.progress() == 200, "progress held at time");
    require(rig.energy.stored() == energy, "no energy while blocked");
    require(rawCopper(rig) == 0, "no product");
    require(rig.fluids.getFluidInTank(0).getAmount() == 1_000, "water untouched");
    require(!rig.items.plan(0).isNone() && !rig.fluids.plan(0).isNone(), "plans held");

    rig.items.setStackInSlot(0, ore);
    rig.ticks(level, 1);
    require(rig.processor.status() == Status.IDLE, "finished once the ore is back");
    require(rawCopper(rig) == 2, "product made");
    require(rig.fluids.getFluidInTank(0).getAmount() == 750, "water taken");
    require(rig.items.getStackInSlot(0).isEmpty(), "ore taken");

    rig = ProcessorRig.crusher(1, 1_000, 10_000);
    rig.ticks(level, 101);
    rig.items.setStackInSlot(0, new ItemStack(Items.DIRT));
    rig.ticks(level, 120);
    require(rig.processor.status() == Status.BLOCKED, "blocked by dirt");
    require(rig.items.getStackInSlot(0).is(Items.DIRT), "dirt kept");
    require(rawCopper(rig) == 0, "no product from dirt");
  }

  /** A product slot filled mid-cycle blocks the finish, and nothing is taken. */
  static void blockedByOutput(ServerLevel level) {
    var rig = ProcessorRig.crusher(3, 1_000, 10_000);
    rig.ticks(level, 101);
    rig.items.setStackInSlot(1, new ItemStack(Items.COBBLESTONE, 64));
    rig.ticks(level, 100);
    require(rig.processor.status() == Status.BLOCKED, "blocked " + rig.processor.status());
    require(rig.items.getStackInSlot(0).getCount() == 3, "ore unchanged");
    require(rig.fluids.getFluidInTank(0).getAmount() == 1_000, "water unchanged");
    require(rig.items.plan(0).take() == 1 && !rig.items.plan(1).isNone(), "plans in place");
    long energy = rig.energy.stored();
    int changes = rig.changes[0];
    rig.ticks(level, 5);
    require(rig.changes[0] == changes, "a blocked machine marked changed");
    require(rig.energy.stored() == energy, "no energy while blocked");
    require(rig.processor.status() == Status.BLOCKED, "still blocked");

    rig.items.setStackInSlot(1, ItemStack.EMPTY);
    rig.ticks(level, 1);
    require(rig.processor.status() == Status.IDLE, "finished once there is room");
    require(rig.items.getStackInSlot(0).getCount() == 2, "ore taken");
    require(rig.fluids.getFluidInTank(0).getAmount() == 750, "water taken");
    require(rawCopper(rig) == 2, "product made");
  }

  /** A machine short of energy by part of a tick waits quietly. */
  static void partialStallIsQuiet(ServerLevel level) {
    var rig = ProcessorRig.crusher(3, 1_000, 3_010);
    rig.ticks(level, 151);
    expect(rig, Status.RUNNING, 150, "tick 150");
    require(rig.energy.stored() == 10, "10 FE left " + rig.energy.stored());
    int changes = rig.changes[0];
    rig.ticks(level, 5);
    expect(rig, Status.STALLED, 150, "stalled");
    require(rig.energy.stored() == 10, "nothing drawn");
    require(rig.changes[0] == changes, "a stalled machine marked changed");
  }

  /** A recipe without an energy group never waits for power. */
  static void noPowerNeeded(ServerLevel level) {
    var rig = ProcessorRig.oneSlot(false);
    rig.items.insertResult(0, new ItemStack(Items.COBBLESTONE), false);
    for (int tick = 0; tick < 6; tick++) {
      rig.ticks(level, 1);
      require(rig.processor.status() != Status.STALLED, "stalled at tick " + tick);
    }
    require(rig.processor.status() == Status.IDLE, "done " + rig.processor.status());
    require(rig.items.getStackInSlot(0).is(Items.GRAVEL), "gravel made");
  }

  /** A machine with nothing to do does not ask to be saved. */
  static void idleDoesNotMarkChanged(ServerLevel level) {
    var rig = ProcessorRig.crusher(0, 0, 0);
    rig.ticks(level, 20);
    require(rig.changes[0] == 0, "empty machine marked changed " + rig.changes[0] + " times");

    rig = ProcessorRig.crusher(3, 0, 0);
    rig.ticks(level, 20);
    require(rig.changes[0] == 0, "machine that can't start marked changed");
    require(rig.processor.status() == Status.IDLE, "idle");
  }

  /** A mid-cycle save and load puts back the same plans and progress, and the same finish. */
  static void reloadResumes(ServerLevel level) {
    var rig = ProcessorRig.crusher(3, 1_000, 100_000);
    rig.ticks(level, 101);
    var copy = reload(level, rig);
    expect(copy, Status.RUNNING, 100, "after the load");
    require(copy.processor.time() == 200, "time comes from the plan");
    for (int slot = 0; slot < 4; slot++)
      require(copy.items.plan(slot).sameAs(rig.items.plan(slot)), "plan " + slot);
    require(copy.fluids.plan(0).sameAs(rig.fluids.plan(0)), "tank plan");
    copy.ticks(level, 99);
    expect(copy, Status.RUNNING, 199, "one tick short");
    copy.ticks(level, 1);
    require(copy.processor.status() == Status.IDLE, "finished on time");
    require(rawCopper(copy) == 2, "products made");
    require(copy.items.getStackInSlot(0).getCount() == 2, "ore taken");
    require(copy.fluids.getFluidInTank(0).getAmount() == 750, "water taken");
    require(unplanned(copy), "plans released");
  }

  /** A running cycle keeps its recipe through a /reload; the next cycle uses the new one. */
  static void recipeChanged(ServerLevel level) {
    var manager = level.getRecipeManager();
    var id = ResourceLocation.fromNamespaceAndPath("mmdlib_smoke", "refining/crush_copper");
    var originals = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
    try {
      var rig = ProcessorRig.crusher(3, 1_000, 1_000_000);
      rig.ticks(level, 101);
      var diamonds =
          MachineRecipeBuilder.recipe(MachineRecipeTypeSmoke.REFINING)
              .inputItems("input", Ingredient.of(Items.COPPER_ORE), 1)
              .inputFluid("water", Fluids.WATER, 250)
              .inputEnergy("energy", 4_000)
              .outputItem("output", new ItemStack(Items.DIAMOND, 5))
              .time(20)
              .build();
      var changed = new ArrayList<>(originals);
      changed.removeIf(holder -> holder.id().equals(id));
      var entry = new MachineRecipeEntry(MachineRecipeTypeSmoke.REFINING, diamonds);
      changed.add(new RecipeHolder<>(id, entry));
      manager.replaceRecipes(changed);
      require(rig.processor.time() == 200, "the cycle keeps its time");
      rig.ticks(level, 99);
      expect(rig, Status.RUNNING, 199, "still the old time");
      rig.ticks(level, 1);
      require(rawCopper(rig) == 2, "the old products, got " + rawCopper(rig));
      require(countOf(rig, Items.DIAMOND) == 0, "no diamonds from the old cycle");
      rig.ticks(level, 1);
      expect(rig, Status.RUNNING, 0, "the next cycle starts");
      require(rig.processor.time() == 20, "on the new recipe, time " + rig.processor.time());
      rig.ticks(level, 20);
      require(countOf(rig, Items.DIAMOND) == 5, "five diamonds");

      manager.replaceRecipes(originals);
      rig = ProcessorRig.crusher(3, 1_000, 1_000_000);
      rig.ticks(level, 101);
      var removed = new ArrayList<>(originals);
      removed.removeIf(holder -> holder.id().equals(id));
      manager.replaceRecipes(removed);
      rig.ticks(level, 100);
      require(rig.processor.status() == Status.IDLE, "finished without its recipe");
      require(rawCopper(rig) == 2, "the products of the removed recipe");
      require(rig.items.getStackInSlot(0).getCount() == 2, "ore taken");
      rig.ticks(level, 5);
      require(rig.processor.status() == Status.IDLE, "nothing more to run");
      require(unplanned(rig), "plans released");
    } finally {
      manager.replaceRecipes(originals);
    }
  }

  /** A saved cycle that can't hold cancels, and a pause survives it. */
  static void planCantHold(ServerLevel level) {
    var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
    for (boolean paused : new boolean[] {false, true}) {
      var rig = ProcessorRig.crusher(3, 1_000, 100_000);
      rig.ticks(level, 51);
      var saved = rig.host.save(ops);
      if (paused) saved.getAsJsonObject().getAsJsonObject("work").addProperty("paused", true);

      var missing = JsonParser.parseString(saved.toString().replace("copper_ore", "no_such_ore"));
      var copy = rig.reloaded(level);
      copy.host.load(ops, missing);
      cancelled(copy, paused, "a missing item");

      copy = rig.reloaded(level);
      copy.items.setStackInSlot(0, ItemStack.EMPTY);
      copy.host.load(ops, saved);
      cancelled(copy, paused, "emptied inputs");

      copy = rig.reloaded(level);
      copy.fluids.tank(0).setFluid(FluidStack.EMPTY);
      copy.host.load(ops, saved);
      cancelled(copy, paused, "emptied tank");
    }

    var iron = new ItemStack(Items.IRON_INGOT);
    var gold = ResourceLocation.fromNamespaceAndPath("mmdlib_smoke", "refining/iron_to_gold");
    var fewer = ProcessorRig.oneSlot(true);
    fewer.items.insertResult(0, iron.copy(), false);
    var outside =
        new CyclePlan(
            gold, 10, List.of(new ItemTake("slot", 1, iron)), List.of(), List.of(), Map.of());
    fewer.processor.restore(new State(false, 3, Optional.empty(), 0, Optional.of(outside)));
    cancelled(fewer, false, "fewer slots");

    var running = ProcessorRig.oneSlot(true);
    running.items.insertResult(0, iron.copy(), false);
    running.ticks(level, 4);
    require(running.processor.status() == Status.RUNNING, "the gold cycle runs");
    var noPower = running.reloaded(level, false);
    noPower.host.load(ops, running.host.save(ops));
    cancelled(noPower, false, "an energy group that is gone");
    require(noPower.items.getStackInSlot(0).is(Items.IRON_INGOT), "the iron stays");
  }

  /** The cycle field drops the whole plan if any part of it won't decode, never a part of it. */
  static void partialPlanCancels(ServerLevel level) {
    var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
    var recipe = ResourceLocation.fromNamespaceAndPath("mmdlib_smoke", "refining/crush_copper");
    var plan =
        new CyclePlan(
            recipe,
            200,
            List.of(
                new ItemTake("input", 0, new ItemStack(Items.COPPER_ORE)),
                new ItemTake("output", 1, new ItemStack(Items.COBBLESTONE))),
            List.of(),
            List.of(),
            Map.of());
    var state = new State(true, 50, Optional.empty(), 0, Optional.of(plan));
    JsonElement json = State.CODEC.encodeStart(ops, state).getOrThrow();
    var broken = JsonParser.parseString(json.toString().replace("cobblestone", "no_such_thing"));
    var decoded = State.CODEC.parse(ops, broken);
    require(decoded.result().isPresent(), "the state still decodes: " + decoded);
    var back = decoded.result().get();
    require(back.cycle().isEmpty(), "no part of a broken plan survives: " + back.cycle());
    require(back.paused() && back.progress() == 50, "the rest of the state is kept");
    var whole = State.CODEC.parse(ops, json).getOrThrow();
    require(whole.cycle().isPresent(), "an intact plan decodes");
  }

  /** Loading the same save twice leaves one cycle's plans, not two. */
  static void loadedTwice(ServerLevel level) {
    var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
    var rig = ProcessorRig.crusher(3, 1_000, 100_000);
    rig.ticks(level, 51);
    Tag saved = rig.host.save(ops);
    var copy = rig.reloaded(level);
    copy.host.load(ops, saved);
    int free = copy.items.freeToTake(0);
    copy.host.load(ops, saved);
    for (int slot = 0; slot < 4; slot++)
      require(copy.items.plan(slot).sameAs(rig.items.plan(slot)), "plan " + slot);
    require(copy.fluids.plan(0).sameAs(rig.fluids.plan(0)), "tank plan");
    require(copy.items.freeToTake(0) == free && free == 2, "free is unchanged: " + free);
    expect(copy, Status.RUNNING, 50, "after the second load");
    copy.ticks(level, 150);
    require(unplanned(copy) && rawCopper(copy) == 2, "one cycle's worth");
  }

  /** Two lanes' plans on one slot restore into the same merged plan in either order. */
  static void lanesRestoreInEitherOrder(ServerLevel level) {
    var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
    var gold = ResourceLocation.fromNamespaceAndPath("mmdlib_smoke", "refining/iron_to_gold");
    var iron = new ItemStack(Items.IRON_INGOT);
    var ingot = new ItemProduct(new ItemStack(Items.GOLD_INGOT), 1);
    var takesIron =
        new CyclePlan(
            gold, 10, List.of(new ItemTake("slot", 0, iron)), List.of(), List.of(), Map.of());
    var addsGold =
        new CyclePlan(
            gold,
            10,
            List.of(),
            List.of(),
            List.of(new Placed("slot", ingot, List.of(new At(0, 1)))),
            Map.of());
    var merged = new SlotPlan(1, new ItemStack(Items.GOLD_INGOT));
    for (boolean goldFirst : new boolean[] {false, true}) {
      var rig = ProcessorRig.oneSlot(true);
      rig.items.insertResult(0, iron.copy(), false);
      var other = rig.lane("other");
      var first = goldFirst ? addsGold : takesIron;
      var second = goldFirst ? takesIron : addsGold;
      rig.processor.restore(new State(false, 3, Optional.empty(), 0, Optional.of(first)));
      other.restore(new State(false, 3, Optional.empty(), 0, Optional.of(second)));
      require(rig.items.plan(0).sameAs(merged), "the source has the merged plan");

      var copy = rig.reloaded(level);
      var copyOther = copy.lane("other");
      copy.host.load(ops, rig.host.save(ops));
      var order = ", gold first " + goldFirst;
      require(copy.processor.status() == Status.RUNNING, "first lane runs" + order);
      require(copyOther.status() == Status.RUNNING, "second lane runs" + order);
      require(copy.items.plan(0).sameAs(merged), "the merged plan" + order);
      copy.ticks(level, 12);
      require(copy.items.getStackInSlot(0).is(Items.GOLD_INGOT), "gold made" + order);
      require(copy.items.getStackInSlot(0).getCount() == 1, "one gold" + order);
      require(unplanned(copy), "plans released" + order);
    }
  }

  /** An interrupt frees every reservation, draws nothing, and lets the items be taken. */
  static void interruptFrees(ServerLevel level) {
    var rig = ProcessorRig.crusher(3, 1_000, 100_000);
    rig.ticks(level, 51);
    require(rig.items.extractItem(0, 3, true).getCount() == 2, "one ore is locked");
    long energy = rig.energy.stored();
    rig.processor.interrupt();
    require(unplanned(rig), "every plan is NONE");
    require(rig.processor.paused(), "paused");
    require(rig.processor.status() == Status.PAUSED, "status " + rig.processor.status());
    rig.ticks(level, 20);
    require(rig.energy.stored() == energy, "no energy drawn");
    require(rig.processor.progress() == 50, "progress kept");
    require(rig.processor.status() == Status.PAUSED, "still paused");
    require(rig.items.extractItem(0, 3, false).getCount() == 3, "all the ore can be taken");
    require(rig.fluids.getFluidInTank(0).getAmount() == 1_000, "water untouched");
  }

  /** An interrupt needs no power: a stalled machine frees everything. */
  static void interruptWorksWhileStalled(ServerLevel level) {
    var rig = ProcessorRig.crusher(3, 1_000, 3_000);
    rig.ticks(level, 156);
    require(rig.processor.status() == Status.STALLED, "stalled " + rig.processor.status());
    require(rig.energy.stored() == 0, "no power");
    rig.processor.interrupt();
    require(unplanned(rig), "every plan is NONE");
    require(rig.processor.status() == Status.PAUSED, "paused");
    require(rig.items.extractItem(0, 3, false).getCount() == 3, "the ore can be taken");
  }

  /** Interrupting a blocked cycle clears it; resume finds nothing to continue and searches. */
  static void interruptClearsBlocked(ServerLevel level) {
    var rig = ProcessorRig.crusher(1, 1_000, 100_000);
    rig.ticks(level, 101);
    var ore = rig.items.getStackInSlot(0);
    rig.items.setStackInSlot(0, ItemStack.EMPTY);
    rig.ticks(level, 100);
    require(rig.processor.status() == Status.BLOCKED, "blocked " + rig.processor.status());
    rig.processor.interrupt();
    require(unplanned(rig), "plans cleared");
    require(rig.processor.status() == Status.PAUSED, "paused");
    rig.processor.resume();
    rig.ticks(level, 1);
    expect(rig, Status.IDLE, 0, "resumed without the ore");
    require(!rig.processor.paused(), "no longer paused");
    rig.items.setStackInSlot(0, ore);
    rig.ticks(level, 1);
    expect(rig, Status.RUNNING, 0, "searching found the ore");
  }

  /** The same recipe on the same contents continues from its progress. */
  static void resumeKeepsProgress(ServerLevel level) {
    var rig = ProcessorRig.crusher(3, 1_000, 100_000);
    rig.ticks(level, 101);
    rig.processor.interrupt();
    rig.ticks(level, 10);
    rig.processor.resume();
    rig.ticks(level, 1);
    expect(rig, Status.RUNNING, 101, "resumed and drew");
    require(rig.items.plan(0).take() == 1 && rig.fluids.plan(0).take() == 250, "reserved again");
    rig.ticks(level, 98);
    expect(rig, Status.RUNNING, 199, "one tick short");
    rig.ticks(level, 1);
    require(rig.processor.status() == Status.IDLE, "finished");
    require(rawCopper(rig) == 2 && rig.items.getStackInSlot(0).getCount() == 2, "products made");
  }

  /** Resuming without power puts the reservations back and waits. */
  static void resumeWithoutPower(ServerLevel level) {
    var rig = ProcessorRig.crusher(3, 1_000, 3_000);
    rig.ticks(level, 151);
    expect(rig, Status.RUNNING, 150, "tick 150");
    rig.processor.interrupt();
    rig.processor.resume();
    rig.ticks(level, 1);
    expect(rig, Status.STALLED, 150, "resumed with an empty buffer");
    require(rig.items.plan(0).take() == 1 && rig.fluids.plan(0).take() == 250, "inputs held");
    require(!rig.items.plan(1).isNone(), "room held");
    rig.energy.setStored(200);
    rig.ticks(level, 1);
    expect(rig, Status.RUNNING, 151, "power back");
  }

  /** Progress is lost when the inputs, the room or the recipe are no longer there. */
  static void resumeResets(ServerLevel level) {
    var rig = ProcessorRig.crusher(3, 1_000, 100_000);
    rig.ticks(level, 101);
    rig.processor.interrupt();
    rig.items.setStackInSlot(0, ItemStack.EMPTY);
    rig.processor.resume();
    rig.ticks(level, 1);
    expect(rig, Status.IDLE, 0, "inputs taken");
    require(unplanned(rig), "nothing reserved");
    rig.items.setStackInSlot(0, new ItemStack(Items.COPPER_ORE, 3));
    rig.ticks(level, 1);
    expect(rig, Status.RUNNING, 0, "searching again");

    rig = ProcessorRig.crusher(3, 1_000, 100_000);
    rig.ticks(level, 101);
    rig.processor.interrupt();
    rig.items.setStackInSlot(1, new ItemStack(Items.COBBLESTONE, 64));
    rig.items.setStackInSlot(2, new ItemStack(Items.COBBLESTONE, 64));
    rig.processor.resume();
    rig.ticks(level, 1);
    expect(rig, Status.IDLE, 0, "the room filled");
    require(unplanned(rig), "nothing reserved");
    rig.items.setStackInSlot(2, ItemStack.EMPTY);
    rig.ticks(level, 1);
    expect(rig, Status.RUNNING, 0, "searching again with room");

    var withdrawn = ProcessorRig.crusher(3, 1_000, 100_000);
    withdrawn.ticks(level, 101);
    withdrawn.processor.interrupt();
    withoutCrushCopper(
        level,
        () -> {
          withdrawn.processor.resume();
          withdrawn.ticks(level, 1);
        });
    expect(withdrawn, Status.IDLE, 0, "the recipe is gone");
    require(unplanned(withdrawn), "nothing reserved");
  }

  /** A pause holds an idle machine too, through a save and load. */
  static void pausedStaysPaused(ServerLevel level) {
    var rig = ProcessorRig.crusher(3, 1_000, 100_000);
    rig.processor.interrupt();
    rig.ticks(level, 20);
    expect(rig, Status.PAUSED, 0, "idle and paused");
    require(unplanned(rig), "nothing started");

    var copy = reload(level, rig);
    require(copy.processor.paused(), "paused after the load");
    copy.ticks(level, 20);
    expect(copy, Status.PAUSED, 0, "still paused after the load");
    require(unplanned(copy), "nothing started after the load");
    copy.processor.resume();
    copy.ticks(level, 1);
    expect(copy, Status.RUNNING, 0, "starts once resumed");

    var quiet = ProcessorRig.crusher(0, 0, 0);
    quiet.processor.interrupt();
    quiet.ticks(level, 1);
    require(quiet.changes[0] == 1, "an interrupt asks to be saved: " + quiet.changes[0]);
    quiet.processor.resume();
    quiet.ticks(level, 1);
    require(quiet.changes[0] == 2, "a resume asks to be saved: " + quiet.changes[0]);
    quiet.ticks(level, 5);
    require(quiet.changes[0] == 2, "and only once");
  }

  /** A paused machine saves which recipe and how far, with no cycle, and resumes after a load. */
  static void pausedSavesItsPlace(ServerLevel level) {
    var rig = ProcessorRig.crusher(3, 1_000, 100_000);
    rig.ticks(level, 51);
    rig.processor.interrupt();
    var state = rig.processor.state();
    var recipe = ResourceLocation.fromNamespaceAndPath("mmdlib_smoke", "refining/crush_copper");
    require(state.paused() && state.progress() == 50, "paused at 50: " + state);
    require(state.resumeRecipe().equals(Optional.of(recipe)), "the recipe: " + state);
    require(state.cycle().isEmpty(), "no cycle");

    var copy = reload(level, rig);
    expect(copy, Status.PAUSED, 50, "after the load");
    require(unplanned(copy), "nothing reserved");
    copy.processor.resume();
    copy.ticks(level, 1);
    expect(copy, Status.RUNNING, 51, "continues from the saved progress");
  }

  /** Progress survives a pause only for a recipe that takes as long as it did. */
  static void resumeAfterRedefinition(ServerLevel level) {
    for (int time : new int[] {20, 400}) {
      for (boolean saved : new boolean[] {false, true}) {
        var rig = ProcessorRig.crusher(3, 1_000, 1_000_000);
        rig.ticks(level, 101);
        rig.processor.interrupt();
        var running = saved ? reload(level, rig) : rig;
        var what = "time " + time + (saved ? " saved" : "");
        replacingCrushCopper(
            level,
            time,
            () -> {
              running.processor.resume();
              running.ticks(level, 1);
              expect(running, Status.RUNNING, 1, "restarted, " + what);
              require(running.processor.time() == time, "the new time, " + what);
              require(countOf(running, Items.DIAMOND) == 0, "nothing finished early, " + what);
            });
      }
    }
  }

  private static void replacingCrushCopper(ServerLevel level, int time, Runnable body) {
    var manager = level.getRecipeManager();
    var id = ResourceLocation.fromNamespaceAndPath("mmdlib_smoke", "refining/crush_copper");
    var originals = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
    var recipe =
        MachineRecipeBuilder.recipe(MachineRecipeTypeSmoke.REFINING)
            .inputItems("input", Ingredient.of(Items.COPPER_ORE), 1)
            .inputFluid("water", Fluids.WATER, 250)
            .inputEnergy("energy", 4_000)
            .outputItem("output", new ItemStack(Items.DIAMOND, 5))
            .time(time)
            .build();
    var changed = new ArrayList<>(originals);
    changed.removeIf(holder -> holder.id().equals(id));
    var entry = new MachineRecipeEntry(MachineRecipeTypeSmoke.REFINING, recipe);
    changed.add(new RecipeHolder<>(id, entry));
    try {
      manager.replaceRecipes(changed);
      body.run();
    } finally {
      manager.replaceRecipes(originals);
    }
  }

  private static void withoutCrushCopper(ServerLevel level, Runnable body) {
    var manager = level.getRecipeManager();
    var id = ResourceLocation.fromNamespaceAndPath("mmdlib_smoke", "refining/crush_copper");
    var originals = new ArrayList<RecipeHolder<?>>(manager.getRecipes());
    var removed = new ArrayList<>(originals);
    removed.removeIf(holder -> holder.id().equals(id));
    try {
      manager.replaceRecipes(removed);
      body.run();
    } finally {
      manager.replaceRecipes(originals);
    }
  }

  private static ProcessorRig reload(ServerLevel level, ProcessorRig rig) {
    var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
    var copy = rig.reloaded(level);
    copy.host.load(ops, rig.host.save(ops));
    return copy;
  }

  /** The processor is idle, or paused if it was, with nothing reserved. */
  private static void cancelled(ProcessorRig rig, boolean paused, String why) {
    var expected = paused ? Status.PAUSED : Status.IDLE;
    require(rig.processor.status() == expected, why + ": " + rig.processor.status());
    require(rig.processor.paused() == paused, why + ": paused " + rig.processor.paused());
    require(rig.processor.progress() == 0 && rig.processor.time() == 0, why + ": progress");
    require(unplanned(rig), why + ": plans left behind");
  }

  private static int countOf(ProcessorRig rig, Item item) {
    int count = 0;
    for (int slot = 0; slot < rig.items.getSlots(); slot++) {
      var stack = rig.items.getStackInSlot(slot);
      if (stack.is(item)) count += stack.getCount();
    }
    return count;
  }

  private static int rawCopper(ProcessorRig rig) {
    int count = 0;
    for (int slot = 1; slot <= 2; slot++) {
      var stack = rig.items.getStackInSlot(slot);
      if (stack.is(Items.RAW_COPPER)) count += stack.getCount();
    }
    return count;
  }

  private static boolean unplanned(ProcessorRig rig) {
    for (int slot = 0; slot < rig.items.getSlots(); slot++)
      if (!rig.items.plan(slot).isNone()) return false;
    return rig.fluids.getTanks() == 0 || rig.fluids.plan(0).isNone();
  }

  private static void expect(ProcessorRig rig, Status status, int progress, String when) {
    require(
        rig.processor.status() == status && rig.processor.progress() == progress,
        when + ": " + rig.processor.status() + " at " + rig.processor.progress());
  }

  private static void require(boolean condition, String what) {
    if (!condition) throw new AssertionError("Processor smoke failed: " + what);
  }
}
