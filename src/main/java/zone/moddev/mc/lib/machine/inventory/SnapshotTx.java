package zone.moddev.mc.lib.machine.inventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A {@link MachineTx} that copies each slot, tank or buffer the first time it is touched and puts
 * the copies back, newest first, if closed without commit. Exact for storages the machine owns. Not
 * nested and not thread-safe.
 */
public final class SnapshotTx implements MachineTx {
  private final Map<Object, Runnable> undo = new LinkedHashMap<>();
  private boolean open = true;
  private boolean committed;

  @Override
  public void commit() {
    checkOpen();
    committed = true;
    open = false;
    undo.clear();
  }

  @Override
  public void close() {
    if (!open) return;
    open = false;
    if (committed) return;
    var steps = new ArrayList<>(undo.values());
    undo.clear();
    for (int i = steps.size() - 1; i >= 0; i--) steps.get(i).run();
  }

  @Override
  public void track(Object key, Supplier<Runnable> capture) {
    checkOpen();
    if (!undo.containsKey(key)) undo.put(key, capture.get());
  }

  private void checkOpen() {
    if (!open)
      throw new IllegalStateException(
          committed ? "Machine transaction already committed" : "Machine transaction closed");
  }
}
