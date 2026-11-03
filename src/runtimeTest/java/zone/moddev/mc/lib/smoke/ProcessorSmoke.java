package zone.moddev.mc.lib.smoke;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.machine.feature.RecipeProcessor.Status;

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
    rig.ticks(level, 5);
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

  /** A product slot filled mid-cycle blocks the finish, and the extract is undone. */
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
    rig.ticks(level, 5);
    require(rig.energy.stored() == energy, "no energy while blocked");
    require(rig.processor.status() == Status.BLOCKED, "still blocked");

    rig.items.setStackInSlot(1, ItemStack.EMPTY);
    rig.ticks(level, 1);
    require(rig.processor.status() == Status.IDLE, "finished once there is room");
    require(rig.items.getStackInSlot(0).getCount() == 2, "ore taken");
    require(rig.fluids.getFluidInTank(0).getAmount() == 750, "water taken");
    require(rawCopper(rig) == 2, "product made");
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
