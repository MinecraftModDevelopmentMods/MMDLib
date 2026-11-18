package zone.moddev.mc.lib.gui;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;

import java.nio.charset.StandardCharsets;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.gui.load.ScreenException;
import zone.moddev.mc.lib.gui.load.ScreenReader;

class MachineUpgradesTest {
    @Test
    void shorthandWithoutMachineContextReportsHowToUseAnExplicitLayout() {
        String xml = """
            <screen xmlns="https://moddev.zone/mmdlib/screen" title="test.title"
                    block-entity="zone.moddev.mc.lib.machine.neoforge.BaseMachine">
              <vbox><machine-upgrades/></vbox>
            </screen>
            """;
        var error = assertThrows(ScreenException.class, () -> ScreenReader.read(
            ResourceLocation.parse("test:upgrades"), "upgrades.xml",
            new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
        assertTrue(
            error.getMessage().contains("requires a machine definition"));
    }
}
