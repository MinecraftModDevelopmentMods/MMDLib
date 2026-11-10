package zone.moddev.mc.lib.smoke;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import zone.moddev.mc.lib.machine.ingredient.FluidAmount;
import zone.moddev.mc.lib.machine.inventory.SlotPlan;
import zone.moddev.mc.lib.machine.inventory.TankPlan;

/**
 * Checks the rules that say what a planned slot or tank holds, offers and
 * accepts.
 */
final class SlotPlanSmoke {
    private SlotPlanSmoke() {
    }

    static void check() {
        var none = ItemStack.EMPTY;

        var take1 = new SlotPlan(1, none);
        var fifty = iron(50);
        require(take1.free(fifty) == 49, "free 49");
        require(take1.accepts(fifty, iron(1))
            && take1.room(fifty, iron(1), 64) == 14, "iron room 14");
        require(!take1.accepts(fifty, gold(1))
            && take1.room(fifty, gold(1), 64) == 0, "gold refused");
        require(take1.endState(fifty).getCount() == 49, "end 49");
        require(take1.endState(iron(1)).isEmpty(),
            "end empty once the player leaves one");

        var swap = new SlotPlan(1, gold(1));
        var one = iron(1);
        require(swap.free(one) == 0, "swap free");
        require(!swap.accepts(one, iron(1)) && !swap.accepts(one, gold(1)),
            "swap refuses both");
        require(swap.legal(one, 64) && swap.endState(one).is(Items.GOLD_INGOT),
            "swap ends as gold");
        require(!swap.legal(iron(2), 64), "partial stack is not legal");
        require(!swap.legal(none, 64), "cannot take from an empty slot");

        var top = new SlotPlan(1, iron(2));
        var sixtyThree = iron(63);
        require(top.free(sixtyThree) == 62, "top free");
        require(top.accepts(sixtyThree, iron(1))
            && top.room(sixtyThree, iron(1), 64) == 0, "top full");
        require(top.legal(sixtyThree, 64), "top legal");
        require(top.endState(sixtyThree).getCount() == 64, "top ends full");
        require(!top.legal(sixtyThree, 16), "limit makes it illegal");

        var addGold = new SlotPlan(0, gold(1));
        require(addGold.free(none) == 0, "add free");
        require(addGold.accepts(none, gold(1))
            && addGold.room(none, gold(1), 64) == 63, "gold 63");
        require(!addGold.accepts(none, iron(1)), "iron refused");
        require(addGold.room(none, iron(1), 64) == 0, "iron no room");

        require(
            take1.accepts(one, iron(1)) && take1.room(one, iron(1), 64) == 63,
            "emptied slot 63");
        require(new SlotPlan(0, none).room(iron(10), iron(1), 16) == 6,
            "limit 16 caps room");
        require(new SlotPlan(0, none).room(none, iron(1), 16) == 16,
            "limit 16 caps an empty slot");

        require(new SlotPlan(0, iron(1)).plus(new SlotPlan(0, gold(1))) == null,
            "different adds");
        var sum = new SlotPlan(1, none).plus(new SlotPlan(2, none));
        require(sum != null && sum.take() == 3 && sum.add().isEmpty(),
            "takes add up");
        var a = new SlotPlan(1, iron(2));
        var b = new SlotPlan(2, iron(3));
        var ab = a.plus(b);
        require(ab != null && ab.take() == 3 && ab.add().getCount() == 5,
            "plus");
        require(ab.minus(b).sameAs(a), "minus undoes plus");
        var c = new SlotPlan(0, gold(1));
        var ac = SlotPlan.NONE.plus(c);
        require(ac != null && ac.sameAs(c) && ac.minus(c).isNone(),
            "plus onto none");
        require(!a.sameAs(b) && a.sameAs(new SlotPlan(1, iron(2))), "sameAs");
        require(new SlotPlan(0, none).isNone() && SlotPlan.NONE.isNone()
            && !a.isNone(), "isNone");

        var source = iron(2);
        var copy = new SlotPlan(0, source);
        source.setCount(9);
        require(copy.add().getCount() == 2, "add is copied in");
        copy.add().setCount(9);
        require(copy.add().getCount() == 2, "add is copied out");

        tank();
    }

    private static void tank() {
        var none = DataComponentPatch.EMPTY;
        var water = new FluidAmount(Fluids.WATER.builtInRegistryHolder(), none,
            1000);
        var lava = new FluidAmount(Fluids.LAVA.builtInRegistryHolder(), none,
            1000);
        var plan = new TankPlan(250, FluidAmount.EMPTY);
        require(plan.free(water) == 750, "tank free");
        require(plan.room(water, water, 4000) == 3000, "tank water room");
        require(!plan.accepts(water, lava) && plan.room(water, lava, 4000) == 0,
            "tank lava refused");
        require(plan.endState(water).amount() == 750, "tank end");

        var swap = new TankPlan(1000, lava);
        require(swap.legal(water, 4000) && swap.endState(water).sameFluid(lava),
            "tank swap");
        require(!new TankPlan(500, lava).legal(water, 4000),
            "tank partial swap illegal");
        require(!new TankPlan(0, lava).legal(water, 4000), "tank mix illegal");
        require(!new TankPlan(0, water).legal(water, 1500),
            "tank over capacity illegal");
        require(new TankPlan(0, water).plus(new TankPlan(0, lava)) == null,
            "tank different adds");
        var both = plan.plus(new TankPlan(100, water));
        require(
            both != null && both.take() == 350 && both.add().amount() == 1000,
            "tank plus");
        require(both.minus(new TankPlan(100, water)).sameAs(plan),
            "tank minus");
        require(TankPlan.NONE.isNone() && !plan.isNone(), "tank isNone");
    }

    private static ItemStack iron(int count) {
        return new ItemStack(Items.IRON_INGOT, count);
    }

    private static ItemStack gold(int count) {
        return new ItemStack(Items.GOLD_INGOT, count);
    }

    private static void require(boolean condition, String what) {
        if (!condition)
            throw new AssertionError("Slot plan smoke failed: " + what);
    }
}
