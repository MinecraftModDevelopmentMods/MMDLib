package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.api.stats.CounterRate;

class EnergyRateTest {
  private static final class EnergyRate {
    final CounterRate in = new CounterRate(20),
        out = new CounterRate(20),
        used = new CounterRate(20);

    void sample(EnergyBuffer buffer, long tick) {
      in.sample(buffer.inserted(), tick);
      out.sample(buffer.extracted(), tick);
      used.sample(buffer.used(), tick);
    }

    String format(boolean input, int unit) {
      return (input ? in : out).format(unit, 0);
    }
  }

  @Test
  void roundsEnergyToGroupedWholeNumbers() {
    var energy = new EnergyBuffer(100000, 100000);
    var rate = new EnergyRate();
    rate.sample(energy, 0);
    energy.insert(19990, false);
    rate.sample(energy, 20);
    assertEquals("1,000", rate.format(true, 1));
    assertEquals("19,990", rate.format(true, 20));
    assertEquals("1,199,400", rate.format(true, 1200));
  }

  @Test
  void rollbackRestoresTransferCounters() {
    var energy = new EnergyBuffer(1000, 1000);
    energy.setStored(100);
    var group = zone.moddev.mc.lib.machine.neoforge.StorageGroups.energy(energy);
    var inventory =
        zone.moddev.mc.lib.machine.inventory.MachineInventory.builder()
            .group("energy", group)
            .build();
    try (var tx = inventory.begin()) {
      group.insert(500, tx);
      group.extract(300, tx);
      group.consume(50, tx);
    }
    assertEquals(100, energy.stored());
    assertEquals(java.math.BigInteger.ZERO, energy.inserted());
    assertEquals(java.math.BigInteger.ZERO, energy.extracted());
    assertEquals(java.math.BigInteger.ZERO, energy.used());
  }

  @Test
  void measuresBalancedFlowAndUnitConversions() {
    var energy = new EnergyBuffer(1000, 1000);
    energy.setStored(500);
    var rate = new EnergyRate();
    rate.sample(energy, 100);
    assertEquals("—", rate.format(true, 1));
    energy.insert(400, false);
    energy.consume(400);
    assertEquals(500, energy.stored());
    rate.sample(energy, 120);
    assertEquals("20", rate.format(true, 1));
    assertEquals("400", rate.used.format(20, 0));
    assertEquals("0", rate.format(false, 20));
    assertEquals("24,000", rate.used.format(1200, 0));
    rate.sample(energy, 140);
    assertEquals("0", rate.format(true, 1));
    assertEquals("0", rate.format(false, 1));
  }

  @Test
  void ignoresSimulationsAssignmentsAndRejectedConsumption() {
    var energy = new EnergyBuffer(1000, 100);
    var rate = new EnergyRate();
    rate.sample(energy, 0);
    energy.insert(100, true);
    energy.setStored(500);
    energy.extract(400, true);
    assertFalse(energy.consume(600));
    energy.setCapacity(200);
    rate.sample(energy, 20);
    assertEquals("0", rate.format(true, 1));
    assertEquals("0", rate.format(false, 1));
  }

  @Test
  void countsAcceptedSidedTransfersAndHandlesLargeTotals() {
    var energy = new EnergyBuffer(Long.MAX_VALUE, Long.MAX_VALUE);
    var rate = new EnergyRate();
    rate.sample(energy, 0);
    energy.insert(Long.MAX_VALUE, false);
    energy.extract(Long.MAX_VALUE, false);
    energy.insert(Long.MAX_VALUE, false);
    rate.sample(energy, 20);
    assertEquals("18,446,744,073,709,551,614", rate.format(true, 20));
    assertEquals("1,106,804,644,422,573,096,840", rate.format(true, 1200));
    energy.setStored(0);
    rate.sample(energy, 20);
    var view = energy.view(zone.moddev.mc.lib.api.capabilities.IoMode.BOTH);
    assertEquals(7, view.receiveEnergy(7, false));
    assertEquals(7, view.extractEnergy(10, false));
    rate.sample(energy, 40);
    assertEquals("0", rate.format(true, 1));
    assertEquals("0", rate.format(false, 1));
  }
}
