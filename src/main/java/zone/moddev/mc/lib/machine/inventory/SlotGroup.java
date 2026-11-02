package zone.moddev.mc.lib.machine.inventory;

/**
 * Some of a machine's slots, tanks or energy, under one key that recipes name ({@code input},
 * {@code water}, {@code energy}). Groups work as the machine itself: slot roles and face modes,
 * which limit automation, don't apply. Indexes are 0-based within the group; an index outside it
 * takes or inserts nothing.
 */
public sealed interface SlotGroup permits ItemGroup, FluidGroup, EnergyGroup {}
