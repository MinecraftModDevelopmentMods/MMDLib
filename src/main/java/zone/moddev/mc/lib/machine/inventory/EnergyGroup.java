package zone.moddev.mc.lib.machine.inventory;

/** An energy buffer in a {@link MachineInventory}; amounts in FE, as {@code long}. */
public non-sealed interface EnergyGroup extends SlotGroup {
  /** The energy stored. */
  long stored();

  /** The most it can store. */
  long capacity();

  /** Takes up to {@code amount}; returns how much was taken. */
  long extract(long amount, MachineTx tx);

  /** Stores up to {@code amount}; returns how much was stored. */
  long insert(long amount, MachineTx tx);
}
