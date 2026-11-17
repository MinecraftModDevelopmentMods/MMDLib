package zone.moddev.mc.lib.machine.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Inclusive block offsets in the machine's local frame: right, up, forward. */
public record WorkingArea(int minRight, int minUp, int minForward, int maxRight,
    int maxUp, int maxForward) {
    public WorkingArea {
        if (minRight > maxRight || minUp > maxUp || minForward > maxForward)
            throw new IllegalArgumentException("Reversed working area");
        for (int value : new int[]{minRight, minUp, minForward, maxRight, maxUp,
            maxForward})
            if (value < -128 || value > 128)
                throw new IllegalArgumentException(
                    "Working area exceeds 128 blocks");
    }

    public AABB bounds(BlockPos origin, Direction front) {
        if (front.getAxis().isVertical())
            throw new IllegalArgumentException(
                "Working area requires horizontal facing");
        var right = front.getClockWise();
        var first = origin.relative(right, minRight).above(minUp)
            .relative(front, minForward);
        var last = origin.relative(right, maxRight).above(maxUp).relative(front,
            maxForward);
        return new AABB(Math.min(first.getX(), last.getX()),
            Math.min(first.getY(), last.getY()),
            Math.min(first.getZ(), last.getZ()),
            Math.max(first.getX(), last.getX()) + 1,
            Math.max(first.getY(), last.getY()) + 1,
            Math.max(first.getZ(), last.getZ()) + 1);
    }

    public int width() {
        return maxRight - minRight + 1;
    }
    public int height() {
        return maxUp - minUp + 1;
    }
    public int depth() {
        return maxForward - minForward + 1;
    }
    public int size() {
        return width() * height() * depth();
    }

    /**
     * Horizontal growth; fields on one side keep their near edge. Height is
     * unchanged.
     */
    public WorkingArea expandHorizontal(int radius) {
        if (radius < 0 || radius > 128)
            throw new IllegalArgumentException("Invalid range growth");
        return new WorkingArea(growMin(minRight, maxRight, radius), minUp,
            growMin(minForward, maxForward, radius),
            growMax(minRight, maxRight, radius), maxUp,
            growMax(minForward, maxForward, radius));
    }

    private static int growMin(int min, int max, int radius) {
        return Math.max(-128,
            min - (min > 0 ? 0 : max < 0 ? 2 * radius : radius));
    }

    private static int growMax(int min, int max, int radius) {
        return Math.min(128,
            max + (max < 0 ? 0 : min > 0 ? 2 * radius : radius));
    }

    /** Zero-based row within a horizontal layer. */
    public int row(int index) {
        java.util.Objects.checkIndex(index, size());
        return index / width() % depth();
    }

    /**
     * Immutable world position, left to right then row by row, bottom layer
     * first. Rear fields start at their near edge; other fields start at
     * minForward.
     */
    public BlockPos blockAt(BlockPos origin, Direction front, int index) {
        if (front.getAxis().isVertical())
            throw new IllegalArgumentException(
                "Working area requires horizontal facing");
        int row = row(index);
        int forward = maxForward < 0 ? maxForward - row : minForward + row;
        return origin.relative(front.getClockWise(), minRight + index % width())
            .above(minUp + index / (width() * depth())).relative(front, forward)
            .immutable();
    }

    /**
     * Lazy enumeration of coordinates; does not read a world or load chunks.
     */
    public Iterable<BlockPos> blocks(BlockPos origin, Direction front) {
        if (front.getAxis().isVertical())
            throw new IllegalArgumentException(
                "Working area requires horizontal facing");
        var fixedOrigin = origin.immutable();
        return () -> new java.util.Iterator<>() {
            private int index;
            public boolean hasNext() {
                return index < size();
            }
            public BlockPos next() {
                if (!hasNext())
                    throw new java.util.NoSuchElementException();
                return blockAt(fixedOrigin, front, index++);
            }
        };
    }

    /**
     * Implement on a processor to expose its current area to shared controls.
     */
    public interface Provider {
        WorkingArea baseWorkingArea();
        default int rangeUpgrades() {
            return 0;
        }
        default WorkingArea workingArea() {
            return RangeUpgrades.apply(baseWorkingArea(), rangeUpgrades());
        }
    }
}
