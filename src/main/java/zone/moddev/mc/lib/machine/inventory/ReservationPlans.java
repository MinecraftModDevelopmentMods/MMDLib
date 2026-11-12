package zone.moddev.mc.lib.machine.inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;

/**
 * Shared plan storage, merging and transaction rollback for item slots and
 * fluid tanks.
 */
public final class ReservationPlans<P> {
    private final List<P> plans;
    private final P empty;
    private final BinaryOperator<P> plus;
    private final BinaryOperator<P> minus;
    private final BiPredicate<Integer, P> legal;
    private long revision;

    public ReservationPlans(int size, P empty, BinaryOperator<P> plus,
        BinaryOperator<P> minus, BiPredicate<Integer, P> legal) {
        this.plans = new ArrayList<>(Collections.nCopies(size, empty));
        this.empty = empty;
        this.plus = plus;
        this.minus = minus;
        this.legal = legal;
    }

    public void add() {
        plans.add(empty);
        revision++;
    }

    public long revision() {
        return revision;
    }

    public P get(int index) {
        return plans.get(index);
    }

    public void set(int index, P plan) {
        plans.set(index, plan);
        revision++;
    }

    public boolean reserve(int index, P plan) {
        var merged = plus.apply(get(index), plan);
        if (merged == null || !legal.test(index, merged))
            return false;
        set(index, merged);
        return true;
    }

    public void release(int index, P plan) {
        set(index, minus.apply(get(index), plan));
    }

    public boolean reserve(int index, P plan, MachineTx tx) {
        track(index, tx);
        return reserve(index, plan);
    }

    /**
     * Restores a saved reservation without requiring its contents to be present
     * yet.
     */
    public boolean restore(int index, P plan, MachineTx tx) {
        var merged = plus.apply(get(index), plan);
        if (merged == null)
            return false;
        track(index, tx);
        set(index, merged);
        return true;
    }

    public void release(int index, P plan, MachineTx tx) {
        track(index, tx);
        release(index, plan);
    }

    private record Key(ReservationPlans<?> owner, int index) {
    }

    private void track(int index, MachineTx tx) {
        tx.track(new Key(this, index), () -> {
            var before = get(index);
            return () -> set(index, before);
        });
    }
}
