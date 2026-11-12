package zone.moddev.mc.lib.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.api.capabilities.IoMode;
import zone.moddev.mc.lib.machine.definition.MachineColors;

class MachineColorsTest {
    @Test
    void namesAndHexResolveToTheSameRgb() {
        MachineColors.palette().forEach((name, rgb) -> {
            assertEquals(rgb.intValue(), MachineColors.parse(name));
            assertEquals(rgb.intValue(),
                MachineColors.parse(String.format("#%06X", rgb)));
        });
        assertEquals(0, MachineColors.parse("#000000"));
        assertEquals(0xffffff, MachineColors.parse("#ffffff"));
        assertThrows(UnsupportedOperationException.class,
            () -> MachineColors.palette().put("other", 0));
        for (var value : List.of("", "#fff", "#12345678", "red",
            "input.primary ", "Input.Primary", "#xyzxyz"))
            assertThrows(IllegalArgumentException.class,
                () -> MachineColors.parse(value));
    }

    @Test
    void roleFamiliesCycleWithoutDependingOnOtherResources() {
        for (int i = 0; i < 12; i++) {
            String variant = List.of("primary", "secondary", "tertiary")
                .get(i % 3);
            assertEquals(MachineColors.parse("input." + variant),
                MachineColors.item(IoMode.INPUT, i));
            assertEquals(MachineColors.parse("output." + variant),
                MachineColors.item(IoMode.OUTPUT, i));
            assertEquals(MachineColors.parse("fluid." + variant),
                MachineColors.fluid(i));
            assertEquals(MachineColors.parse("storage"),
                MachineColors.item(IoMode.BOTH, i));
            assertEquals(MachineColors.parse("manual"),
                MachineColors.item(IoMode.DISABLED, i));
        }
    }
}
