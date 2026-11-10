package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.model.Common;
import zone.moddev.mc.lib.gui.model.GaugeFill;
import zone.moddev.mc.lib.gui.model.Orientation;
import zone.moddev.mc.lib.gui.model.elements.FluidTankElement;
import zone.moddev.mc.lib.gui.model.elements.GaugeElement;

class GaugeFillTest {
    @Test
    void defaultAndConfiguredSolidFillsRemainIndependentOfValue() {
        var plain = new GaugeElement(Common.empty(), Orientation.VERTICAL, 36,
            Optional.empty());
        var coloured = new GaugeElement(Common.empty(), Orientation.HORIZONTAL,
            36, Optional.of(0x123456));
        assertEquals(GaugeFill.solid(0x777777), plain.fill(42));
        assertEquals(GaugeFill.solid(0x123456), coloured.fill(null));
        assertTrue(coloured.fill(12).fluid().isEmpty());
    }

    @Test
    void tankSubclassCanChooseSolidFillWithoutChangingRatioOrDisplay() {
        var tank = new FluidTankElement(Common.empty(), 0, Orientation.VERTICAL,
            36) {
            public GaugeFill fill(Object value) {
                return GaugeFill.solid(0xaabbcc);
            }
        };
        assertEquals(GaugeFill.solid(0xaabbcc), tank.fill(5));
        assertEquals(.5, tank.fillRatio(5, 10));
        assertEquals(5, tank.displayValue(5));
    }
}
