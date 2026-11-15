package zone.moddev.mc.lib.gui.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class SlotRendererTest {
    @Test
    void groupWashPreservesNeutralAndExistingSlotShading() {
        assertEquals(0xf0cd82,
            ElementDrawing.backgroundTint(0xf0cd82, Optional.empty()));
        assertEquals(0xf0cd82,
            ElementDrawing.backgroundTint(0xf0cd82, Optional.of(0xffffffff)));
        assertEquals(0xff9999,
            ElementDrawing.backgroundTint(0xffffff, Optional.of(0xffff0000)));
        assertEquals(0x804c4c,
            ElementDrawing.backgroundTint(0x808080, Optional.of(0xffff0000)));
        assertEquals(0,
            ElementDrawing.backgroundTint(0, Optional.of(0xffff0000)));
    }
}
