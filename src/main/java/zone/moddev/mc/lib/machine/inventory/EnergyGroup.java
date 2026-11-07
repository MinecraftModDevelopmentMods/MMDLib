package zone.moddev.mc.lib.machine.inventory;

/** An energy buffer in a {@link MachineInventory}; amounts in FE, as {@code long}. */
public non-sealed interface EnergyGroup extends SlotGroup {
  /** The energy stored. */
  long stored();

  /** The most it can store. */
  long capacity();

  /** Takes up to {@code amount}; returns how much was taken. */
  long extract(long amount, MachineTx tx);

  /** Processing use, distinct from exporting energy where the storage supports telemetry. */
  default long consume(long amount, MachineTx tx) {
    return extract(amount, tx);
  }

  /** Shared identity used to attribute processor maximum demand to its backing storage. */
  default Object statisticsResource() {
    return this;
  }

  /** Stores up to {@code amount}; returns how much was stored. */
  long insert(long amount, MachineTx tx);
}
