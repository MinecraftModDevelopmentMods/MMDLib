package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;

import java.nio.charset.StandardCharsets;

import java.util.Map;

import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.items.IItemHandlerModifiable;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.api.capabilities.EnergyBuffer;
import zone.moddev.mc.lib.gui.load.ScreenReader;
import zone.moddev.mc.lib.gui.model.elements.EnergyGaugeElement;
import zone.moddev.mc.lib.gui.runtime.ScreenHost;
import zone.moddev.mc.lib.gui.runtime.ScreenValues;

class EnergyGaugeElementTest {
    private EnergyGaugeElement read(String attributes) throws Exception {
        var xml = "<screen xmlns=\"https://moddev.zone/mmdlib/screen\" title=\"test.energy\""
            + " block-entity=\""
            + ElementInteractionTest.Host.class.getName()
            + "\">"
            + "<energy-gauge "
            + attributes
            + "/></screen>";
        return (EnergyGaugeElement) ScreenReader.read(
            ResourceLocation.parse("test:energy"),
            "energy.xml",
            new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
            .body()
            .getFirst();
    }

    @Test
    void javaAndXmlElementsOwnSnapshotsWithoutNarrowingLongEnergy() throws Exception {
        var xml = read("");
        var element = new EnergyGaugeElement(xml.common(), xml.orientation(), xml.length(),
            xml.color());
        var energy = new EnergyBuffer(Long.MAX_VALUE, 1000);
        ScreenHost host = new ScreenHost() {
            public IItemHandlerModifiable screenItems() {
                return null;
            }

            public EnergyBuffer screenEnergy() {
                return energy;
            }
        };
        energy.setStored(Long.MAX_VALUE - 1);
        var before = element.snapshot(host).orElseThrow();
        assertEquals(before, xml.snapshot(host).orElseThrow());
        assertEquals(Long.MAX_VALUE - 1, before.value());
        assertEquals(Long.MAX_VALUE, before.maximum());
        assertEquals(
            java.math.BigInteger.valueOf(Long.MAX_VALUE - 1),
            ScreenValues.snapshot(before.value()));
        assertEquals(xml.tooltipText(), element.tooltipText());
        assertFalse(element.interactive());
        energy.setStored(0);
        assertEquals(0L, element.snapshot(host).orElseThrow().value());
        assertEquals(Long.MAX_VALUE - 1, before.value());
        assertThrows(IllegalArgumentException.class, () -> element.snapshot(() -> null));
    }

    @Test
    void validatesEnergyAndAnyAttachedInteractionResources() throws Exception {
        var gauge = read("interaction=\"mmdlib:fluid-container\" interaction-target=\"2\"");
        assertTrue(gauge.interactive());
        assertDoesNotThrow(() -> gauge.validateResources(Map.of("energy", 1, "fluids", 3)));
        assertThrows(
            IllegalArgumentException.class,
            () -> gauge.validateResources(Map.of("energy", 1, "fluids", 2)));
        assertThrows(
            IllegalArgumentException.class, () -> gauge.validateResources(Map.of("fluids", 3)));
    }
}
