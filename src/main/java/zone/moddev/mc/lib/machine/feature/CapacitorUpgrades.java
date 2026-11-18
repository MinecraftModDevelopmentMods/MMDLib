package zone.moddev.mc.lib.machine.feature;

import java.util.function.IntSupplier;

import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;

/**
 * Recomputes energy limits from fixed base values, never from previously
 * upgraded limits.
 */
public final class CapacitorUpgrades {
    private final EnergyBuffer energy;
    private final long capacity;
    private final long input;
    private final long output;
    private final IntSupplier installed;

    public CapacitorUpgrades(EnergyBuffer energy, long capacity, long input,
        long output, IntSupplier installed) {
        this.energy = java.util.Objects.requireNonNull(energy);
        this.capacity = Math.max(0, capacity);
        this.input = Math.max(0, input);
        this.output = Math.max(0, output);
        this.installed = java.util.Objects.requireNonNull(installed);
    }

    /**
     * Call on socket changes, including after loading its contents and before
     * loading energy.
     */
    public void refresh() {
        int count = Math.clamp(installed.getAsInt(), 0, 8);
        energy.setRates(scaled(input, count), scaled(output, count));
        energy.setCapacity(scaled(capacity, count));
    }

    private static long scaled(long base, int count) {
        long bonus = base / 8 * count + base % 8 * count / 8;
        return base > Long.MAX_VALUE - bonus ? Long.MAX_VALUE : base + bonus;
    }
}
