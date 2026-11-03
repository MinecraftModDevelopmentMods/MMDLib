package zone.moddev.mc.lib.smoke;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import zone.moddev.mc.lib.api.capabilities.FluidStorage;
import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.api.capabilities.ItemStorage;
import zone.moddev.mc.lib.api.capabilities.Role;
import zone.moddev.mc.lib.machine.ingredient.FluidAmount;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;
import zone.moddev.mc.lib.machine.inventory.TankPlan;

/** Checks that storages hold reservation plans and refuse what would break them. */
final class ReservedStorageSmoke {
  private ReservedStorageSmoke() {}

  static void check(ServerLevel level, BlockPos pos) {
    var none = ItemStack.EMPTY;

    var taking = new ItemStorage(1);
    taking.setStackInSlot(0, iron(50));
    taking.setPlan(0, new SlotPlan(1, none));
    require(taking.hasPlan(0) && !taking.hasPlan(1) && !taking.hasPlan(-1), "hasPlan");
    require(taking.freeToTake(0) == 49, "free to take");
    var viewed = taking.view(IoMode.BOTH).extractItem(0, 64, true);
    require(viewed.getCount() == 49, "view takes 49");
    var taken = taking.extractItem(0, 64, false);
    require(taken.getCount() == 49 && taking.getStackInSlot(0).getCount() == 1, "takes 49");
    require(taking.extractItem(0, 64, false).isEmpty(), "the planned one stays");

    var jam = new ItemStorage(1);
    jam.setStackInSlot(0, iron(1));
    jam.setPlan(0, new SlotPlan(1, gold(1)));
    var jamView = jam.view(IoMode.BOTH);
    require(jamView.insertItem(0, iron(1), false).getCount() == 1, "no iron into the jam");
    require(jamView.insertItem(0, gold(1), false).getCount() == 1, "no gold into the jam");
    require(!jamView.isItemValid(0, iron(1)) && !jam.isItemValid(0, gold(1)), "invalid both");

    var rows = new ItemStorage(1);
    rows.setStackInSlot(0, iron(50));
    rows.setPlan(0, new SlotPlan(1, none));
    require(rows.isItemValid(0, iron(1)) && !rows.isItemValid(0, gold(1)), "rows valid");
    require(rows.insertItem(0, iron(30), true).getCount() == 16, "room is 14");
    rows.setStackInSlot(0, iron(63));
    rows.setPlan(0, new SlotPlan(1, iron(2)));
    require(rows.insertItem(0, iron(5), true).getCount() == 5, "top slot refuses");
    rows.setPlan(0, new SlotPlan(0, none));
    require(rows.insertItem(0, iron(5), true).getCount() == 4, "empty plan, 1 of room");

    var empty = new ItemStorage(1);
    empty.setPlan(0, new SlotPlan(0, gold(1)));
    require(empty.insertItem(0, iron(1), true).getCount() == 1, "empty slot refuses iron");
    require(!empty.isItemValid(0, iron(1)) && empty.isItemValid(0, gold(1)), "empty validity");
    require(empty.insertItem(0, gold(64), false).getCount() == 1, "empty slot takes 63 gold");
    require(empty.getStackInSlot(0).getCount() == 63, "63 gold held");

    var own = new ItemStorage(1);
    own.setStackInSlot(0, iron(63));
    own.setPlan(0, new SlotPlan(1, iron(2)));
    require(own.insertResult(0, iron(1), true).getCount() == 1, "insertResult respects plans");
    require(own.insertResult(0, iron(1), false).getCount() == 1, "and when it runs");
    require(own.getStackInSlot(0).getCount() == 63, "nothing moved");
    own.setStackInSlot(0, gold(3));
    require(own.getStackInSlot(0).is(Items.GOLD_INGOT), "setStackInSlot ignores plans");

    var res = new ItemStorage(1);
    res.setStackInSlot(0, iron(1));
    require(res.reserve(0, new SlotPlan(1, gold(1))), "reserve take and add");
    require(!res.reserve(0, new SlotPlan(0, iron(2))), "different add refused");
    require(res.plan(0).sameAs(new SlotPlan(1, gold(1))), "refusal keeps the plan");
    require(!res.reserve(0, new SlotPlan(0, gold(64))), "over the limit refused");
    require(res.plan(0).sameAs(new SlotPlan(1, gold(1))), "limit refusal keeps the plan");
    require(res.reserve(0, new SlotPlan(0, gold(2))), "same item merges");
    require(res.plan(0).add().getCount() == 3, "merged to 3");
    res.release(0, new SlotPlan(0, gold(2)));
    res.release(0, new SlotPlan(1, gold(1)));
    require(res.plan(0).isNone() && !res.hasPlan(0), "release returns to none");
    require(!res.reserve(0, new SlotPlan(2, none)), "cannot take more than held");
    var partial = new ItemStorage(1);
    partial.setStackInSlot(0, iron(2));
    require(!partial.reserve(0, new SlotPlan(1, gold(1))), "partial stack swap refused");
    require(partial.plan(0).isNone(), "partial refusal leaves no plan");
    var limited = new ItemStorage(1).limit(16, 0);
    require(!limited.reserve(0, new SlotPlan(0, iron(17))), "slot limit applies");

    var plain = new ItemStorage(1).limit(16, 0);
    require(plain.maxHeld(0, iron(1)) == 16, "no plan: slot limit");
    require(new ItemStorage(1).maxHeld(0, new ItemStack(Items.ENDER_PEARL)) == 16, "stack max");
    taking.setStackInSlot(0, iron(50));
    taking.setPlan(0, new SlotPlan(1, none));
    require(taking.maxHeld(0, iron(1)) == 64, "planned: total held");
    require(taking.maxHeld(0, gold(1)) == 50, "planned: other item gains no room");

    var changes = new int[1];
    var quiet = new ItemStorage(2).onChange(() -> changes[0]++);
    quiet.setStackInSlot(0, iron(10));
    changes[0] = 0;
    var quietView = quiet.view(IoMode.BOTH);
    quiet.setPlan(0, new SlotPlan(1, none));
    quiet.extractItem(0, 5, true);
    quietView.extractItem(0, 5, true);
    quiet.insertItem(0, iron(5), true);
    quietView.insertItem(1, gold(5), true);
    quiet.isItemValid(0, iron(1));
    quiet.maxHeld(0, iron(1));
    quiet.reserve(1, new SlotPlan(0, gold(1)));
    quiet.release(1, new SlotPlan(0, gold(1)));
    require(changes[0] == 0, "no onChange for simulations and plans: " + changes[0]);

    var broken = new ItemStorage(2);
    broken.setStackInSlot(0, iron(10));
    broken.setStackInSlot(1, gold(2));
    broken.setPlan(0, new SlotPlan(4, iron(3)));
    broken.setPlan(1, new SlotPlan(0, gold(1)));
    broken.dropAll(level, pos);
    require(broken.getStackInSlot(0).isEmpty() && broken.getStackInSlot(1).isEmpty(), "emptied");
    int dropped = 0;
    for (var drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2))) {
      dropped += drop.getItem().getCount();
      drop.discard();
    }
    require(dropped == 12, "dropped everything, planned or not: " + dropped);

    tanks();
  }

  private static void tanks() {
    var none = DataComponentPatch.EMPTY;
    var water = new FluidAmount(Fluids.WATER.builtInRegistryHolder(), none, 1000);
    var lava = new FluidAmount(Fluids.LAVA.builtInRegistryHolder(), none, 1000);

    var one = new FluidStorage();
    one.addTank(4000, Role.STORAGE);
    one.tank(0).setFluid(new FluidStack(Fluids.WATER, 1000));
    one.setPlan(0, new TankPlan(250, FluidAmount.EMPTY));
    require(one.drain(4000, FluidAction.SIMULATE).getAmount() == 750, "handler drain 750");
    var byFluid = one.drain(new FluidStack(Fluids.WATER, 4000), FluidAction.SIMULATE);
    require(byFluid.getAmount() == 750, "handler drain by fluid 750");
    require(one.tank(0).drain(4000, FluidAction.EXECUTE).getAmount() == 750, "tank drain 750");
    require(one.tank(0).getFluidAmount() == 250, "250 stay");
    one.tank(0).setFluid(new FluidStack(Fluids.WATER, 1000));
    require(one.tank(0).getFluidAmount() == 1000, "setFluid ignores plans");

    var swap = new FluidStorage();
    swap.addTank(4000, Role.STORAGE);
    swap.tank(0).setFluid(new FluidStack(Fluids.WATER, 1000));
    swap.setPlan(0, new TankPlan(1000, lava));
    require(swap.tank(0).fill(new FluidStack(Fluids.LAVA, 100), FluidAction.EXECUTE) == 0, "lava");
    require(swap.tank(0).fill(new FluidStack(Fluids.WATER, 100), FluidAction.EXECUTE) == 0, "wet");
    require(swap.tank(0).getFluidAmount() == 1000, "unchanged");

    var two = new FluidStorage();
    two.addTank(1000, Role.STORAGE);
    two.addTank(1000, Role.STORAGE);
    two.tank(0).setFluid(new FluidStack(Fluids.WATER, 1000));
    two.tank(1).setFluid(new FluidStack(Fluids.WATER, 1000));
    two.setPlan(0, new TankPlan(1000, FluidAmount.EMPTY));
    var drained = two.drain(100, FluidAction.EXECUTE);
    require(drained.getAmount() == 100 && two.tank(0).getFluidAmount() == 1000, "drain skips");
    require(two.tank(1).getFluidAmount() == 900, "drain took from the second");
    var named = two.drain(new FluidStack(Fluids.WATER, 100), FluidAction.EXECUTE);
    require(named.getAmount() == 100 && two.tank(0).getFluidAmount() == 1000, "named skips");

    two.tank(0).setFluid(new FluidStack(Fluids.WATER, 500));
    two.tank(1).setFluid(new FluidStack(Fluids.WATER, 500));
    two.setPlan(0, new TankPlan(0, water.withAmount(500)));
    require(two.fill(new FluidStack(Fluids.WATER, 100), FluidAction.EXECUTE) == 100, "fill 100");
    require(two.tank(0).getFluidAmount() == 500, "first tank room is reserved");
    require(two.tank(1).getFluidAmount() == 600, "fill went to the second");

    var reserve = new FluidStorage();
    reserve.addTank(1000, Role.STORAGE);
    reserve.tank(0).setFluid(new FluidStack(Fluids.WATER, 500));
    require(!reserve.reserve(0, new TankPlan(300, lava)), "partial swap refused");
    require(reserve.reserve(0, new TankPlan(0, water.withAmount(400))), "reserve");
    require(!reserve.reserve(0, new TankPlan(0, lava)), "different fluid refused");
    require(!reserve.reserve(0, new TankPlan(0, water.withAmount(200))), "over capacity refused");
    require(reserve.plan(0).sameAs(new TankPlan(0, water.withAmount(400))), "plan kept");
    reserve.release(0, new TankPlan(0, water.withAmount(400)));
    require(reserve.plan(0).isNone(), "released");
  }

  private static ItemStack iron(int count) {
    return new ItemStack(Items.IRON_INGOT, count);
  }

  private static ItemStack gold(int count) {
    return new ItemStack(Items.GOLD_INGOT, count);
  }

  private static void require(boolean condition, String what) {
    if (!condition) throw new AssertionError("Reserved storage smoke failed: " + what);
  }
}
