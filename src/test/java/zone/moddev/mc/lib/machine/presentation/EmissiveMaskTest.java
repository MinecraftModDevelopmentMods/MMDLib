package zone.moddev.mc.lib.machine.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EmissiveMaskTest {
    @Test
    void partitionsFrameAndDotsWithoutLightingTheCenterOrOverlappingRegions() {
        int width = 32, height = 32;
        var mask = EmissiveMask.read(width, height,
            (x, y) -> (x >= 6 && x < 26 && y >= 6 && y < 26
                && !(x >= 8 && x < 24 && y >= 8 && y < 24))
                || (x == 2 && y == 28) ? 1 : 0);
        int[][] visits = new int[height][width];
        boolean[][] lit = new boolean[height][width];
        for (var region : mask.regions())
            for (int y = region.y0(); y < region.y1(); y++)
                for (int x = region.x0(); x < region.x1(); x++) {
                    visits[y][x]++;
                    lit[y][x] = region.emissive();
                }
        int glowingPixels = 0;
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                assertEquals(1, visits[y][x],
                    "Each mask pixel belongs to exactly one region");
                if (lit[y][x])
                    glowingPixels++;
            }
        assertEquals(145, glowingPixels);
        assertEquals(false, lit[16][16]);
        assertEquals(true, lit[6][6]);
        assertEquals(true, lit[28][2]);
    }

    @Test
    void uniformMasksMergeToOneRegionAndExcessiveMasksFailEarly() {
        assertEquals(1,
            EmissiveMask.read(32, 32, (x, y) -> 1).regions().size());
        assertEquals(1,
            EmissiveMask.read(32, 32, (x, y) -> 0).regions().size());
        assertThrows(IllegalArgumentException.class,
            () -> EmissiveMask.read(257, 32, (x, y) -> 0));
        assertThrows(IllegalArgumentException.class,
            () -> EmissiveMask.read(128, 128, (x, y) -> (x + y) % 2));
    }
}
