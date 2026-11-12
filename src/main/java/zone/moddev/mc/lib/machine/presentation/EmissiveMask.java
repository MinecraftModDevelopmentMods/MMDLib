package zone.moddev.mc.lib.machine.presentation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntBinaryOperator;

/**
 * Immutable partition of a binary mask, built once at resource load rather than
 * while rendering.
 */
public final class EmissiveMask {
    public record Region(int x0, int y0, int x1, int y1, boolean emissive) {
    }
    private record Run(int x0, int x1, boolean emissive) {
    }
    private final int width;
    private final int height;
    private final List<Region> regions;

    private EmissiveMask(int width, int height, List<Region> regions) {
        this.width = width;
        this.height = height;
        this.regions = List.copyOf(regions);
    }

    public int width() {
        return width;
    }
    public int height() {
        return height;
    }
    public List<Region> regions() {
        return regions;
    }

    /**
     * Nonzero pixels are emissive. Adjacent equal runs merge vertically; both
     * classes are retained.
     */
    public static EmissiveMask read(int width, int height,
        IntBinaryOperator pixel) {
        if (width < 1 || height < 1 || width > 256 || height > 256)
            throw new IllegalArgumentException(
                "Emissive mask dimensions must be 1..256 pixels");
        var result = new ArrayList<Region>();
        Map<Run, Region> previous = Map.of();
        for (int y = 0; y < height; y++) {
            var current = new HashMap<Run, Region>();
            int x = 0;
            while (x < width) {
                int start = x;
                boolean lit = pixel.applyAsInt(x++, y) != 0;
                while (x < width && (pixel.applyAsInt(x, y) != 0) == lit)
                    x++;
                var run = new Run(start, x, lit);
                var above = previous.get(run);
                current.put(run, new Region(start,
                    above == null ? y : above.y0(), x, y + 1, lit));
            }
            for (var entry : previous.entrySet())
                if (!current.containsKey(entry.getKey()))
                    result.add(entry.getValue());
            if (result.size() + current.size() > 4096)
                throw new IllegalArgumentException(
                    "Emissive mask exceeds 4096 regions; simplify the mask");
            previous = current;
        }
        result.addAll(previous.values());
        return new EmissiveMask(width, height, result);
    }
}
