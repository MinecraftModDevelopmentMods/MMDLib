package zone.moddev.mc.lib.machine.inventory;

import org.jetbrains.annotations.Nullable;

import zone.moddev.mc.lib.machine.ingredient.FluidAmount;

/**
 * What a machine means to do to one tank, as {@link SlotPlan} does for an item
 * slot.
 */
public record TankPlan(int take, FluidAmount add) {
    /** No plan: the tank behaves as if nothing were reserved. */
    public static final TankPlan NONE = new TankPlan(0, FluidAmount.EMPTY);

    /**
     * @throws IllegalArgumentException
     *             if {@code take} is negative
     */
    public TankPlan {
        if (take < 0)
            throw new IllegalArgumentException("Take is negative: " + take);
    }

    /** Whether this takes and adds nothing. */
    public boolean isNone() {
        return take == 0 && add.isEmpty();
    }

    /**
     * How much of {@code held} nobody has planned to take, and so is free to
     * others.
     */
    public int free(FluidAmount held) {
        return Math.max(0, held.amount() - take);
    }

    /**
     * What {@code held} becomes. Check {@link #legal} first: an {@code add} of
     * another fluid is dropped, and the leftover comes back unchanged.
     */
    public FluidAmount endState(FluidAmount held) {
        var left = free(held);
        if (left == 0)
            return add.isEmpty() ? FluidAmount.EMPTY : add;
        if (add.isEmpty() || !held.sameFluid(add))
            return held.withAmount(left);
        return held.withAmount(left + add.amount());
    }

    /**
     * Whether the plan can run on {@code held}: enough to take, one fluid,
     * within capacity.
     */
    public boolean legal(FluidAmount held, int capacity) {
        if (take > held.amount())
            return false;
        var left = free(held);
        if (add.isEmpty())
            return true;
        if (left > 0 && !held.sameFluid(add))
            return false;
        return (long) left + add.amount() <= capacity;
    }

    /**
     * Whether {@code fluid} is the fluid the tank holds now and will hold after
     * the plan.
     */
    public boolean accepts(FluidAmount held, FluidAmount fluid) {
        if (fluid.isEmpty())
            return false;
        if (!held.isEmpty() && !held.sameFluid(fluid))
            return false;
        var end = endState(held);
        return end.isEmpty() || end.sameFluid(fluid);
    }

    /**
     * How much of {@code fluid} may go in now without breaking the plan; 0
     * unless accepted.
     */
    public int room(FluidAmount held, FluidAmount fluid, int capacity) {
        if (!accepts(held, fluid))
            return 0;
        return Math.max(0, Math.min(capacity - endState(held).amount(),
            capacity - held.amount()));
    }

    /**
     * This plan and {@code other} together, or null if they add different
     * fluids.
     */
    @Nullable
    public TankPlan plus(TankPlan other) {
        if (!add.isEmpty() && !other.add.isEmpty() && !add.sameFluid(other.add))
            return null;
        var base = add.isEmpty() ? other.add : add;
        var amount = add.amount() + other.add.amount();
        return new TankPlan(take + other.take,
            amount == 0 ? FluidAmount.EMPTY : base.withAmount(amount));
    }

    /**
     * This plan without {@code other}, which must have been added with
     * {@link #plus}.
     */
    public TankPlan minus(TankPlan other) {
        var rest = Math.max(0, add.amount() - other.add.amount());
        return new TankPlan(Math.max(0, take - other.take),
            rest == 0 ? FluidAmount.EMPTY : add.withAmount(rest));
    }

    /** Whether {@code other} plans the same thing. */
    public boolean sameAs(TankPlan other) {
        if (take != other.take)
            return false;
        if (add.isEmpty() || other.add.isEmpty())
            return add.isEmpty() == other.add.isEmpty();
        return add.sameFluid(other.add) && add.amount() == other.add.amount();
    }
}
