package zone.moddev.mc.lib.machine.inventory;

import java.util.function.Supplier;

/**
 * Changes to a machine's groups that land together or not at all. Closing
 * without {@link #commit()} undoes everything done under this transaction:
 *
 * <pre>{@code
 * try (MachineTx tx = inventory.begin()) {
 *     if (output.insert(result, tx) < result.getCount())
 *         return; // closed without commit: undone
 *     tx.commit();
 * }
 * }</pre>
 */
public interface MachineTx extends AutoCloseable {
    /**
     * Keeps every change made under this transaction.
     *
     * @throws IllegalStateException
     *             if already committed or closed
     */
    void commit();

    /**
     * Ends the transaction, undoing its changes unless it was committed. Safe
     * to call twice.
     */
    @Override
    void close();

    /**
     * Called by a group before it changes the state identified by {@code key}
     * (one slot, tank or buffer). The first time a key is seen, {@code capture}
     * runs and returns how to restore that state; later calls for the same key
     * do nothing.
     *
     * @throws IllegalStateException
     *             if already committed or closed
     */
    void track(Object key, Supplier<Runnable> capture);
}
