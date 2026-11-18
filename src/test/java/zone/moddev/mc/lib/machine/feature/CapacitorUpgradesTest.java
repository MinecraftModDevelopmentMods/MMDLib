package zone.moddev.mc.lib.machine.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;

class CapacitorUpgradesTest {
    @Test
    void everyCountScalesCapacityAndBothAutomationRatesFromTheBase() {
        var count = new AtomicInteger();
        var energy = new EnergyBuffer(100000, 1000, 400);
        var capacitors = new CapacitorUpgrades(energy, 100000, 1000, 400,
            count::get);
        for (int n = 0; n <= 10; n++) {
            count.set(n);
            capacitors.refresh();
            capacitors.refresh(); // Other slots in the same inventory may also
                                  // notify.
            int bounded = Math.min(n, 8);
            assertEquals(100000 + 12500L * bounded, energy.capacity());
            energy.setStored(0);
            assertEquals(1000 + 125 * bounded,
                energy.receiveEnergy(9999, true));
            energy.setStored(energy.capacity());
            assertEquals(400 + 50 * bounded, energy.extractEnergy(9999, true));
        }
    }

    @Test
    void changesPreserveStoredEnergyUntilCapacityShrinksBelowIt() {
        var count = new AtomicInteger(8);
        var energy = new EnergyBuffer(100000, 1000, 0);
        energy.setStored(50000);
        var capacitors = new CapacitorUpgrades(energy, 100000, 1000, 0,
            count::get);
        capacitors.refresh();
        assertEquals(50000, energy.stored());
        assertFalse(energy.canExtract());
        energy.setStored(180000);
        count.set(4);
        capacitors.refresh();
        assertEquals(150000, energy.stored());
        count.set(0);
        capacitors.refresh();
        assertEquals(100000, energy.stored());
        assertEquals(0, energy.used().longValueExact());
    }

    @Test
    void wholeFeRoundingAndLargeValuesNeverOverflow() {
        var energy = new EnergyBuffer(Long.MAX_VALUE, 7, 0);
        var count = new AtomicInteger(1);
        var capacitors = new CapacitorUpgrades(energy, Long.MAX_VALUE, 7, 0,
            count::get);
        capacitors.refresh();
        assertEquals(Long.MAX_VALUE, energy.capacity());
        assertEquals(7, energy.receiveEnergy(100, true));
        count.set(8);
        capacitors.refresh();
        assertEquals(14, energy.receiveEnergy(100, true));
        count.set(-1);
        capacitors.refresh();
        assertEquals(7, energy.receiveEnergy(100, true));
    }

    @Test
    void rateChangesNotifyObserversOnlyWhenLimitsChange() {
        var energy = new EnergyBuffer(1000, 100, 50);
        var notifications = new AtomicInteger();
        energy.changes().subscribe(notifications::incrementAndGet);
        energy.setRates(200, 100);
        energy.setRates(200, 100);
        assertEquals(1, notifications.get());
    }
}
