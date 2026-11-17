package zone.moddev.mc.lib.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.machine.feature.WorkingArea;

class WorkingAreaTest {
    @Test
    void rearExpansionAndTraversalAgreeForEveryFacingAndRange() {
        var base = new WorkingArea(-1, 0, -3, 1, 0, -1);
        var origin = new BlockPos(10, 64, 20);
        for (int count = 0; count <= 9; count++) {
            var area = base.expandHorizontal(count);
            assertEquals(3 + 2 * count, area.width());
            assertEquals(area.width(), area.depth());
            assertEquals(1, area.height());
            for (var front : Direction.Plane.HORIZONTAL) {
                var positions = new java.util.ArrayList<BlockPos>();
                area.blocks(origin, front).forEach(positions::add);
                assertEquals(area.size(), positions.size());
                assertEquals(area.size(),
                    new java.util.HashSet<>(positions).size());
                assertTrue(!positions.contains(origin));
                for (int index = 0; index < positions.size(); index++) {
                    var expected = origin
                        .relative(front.getClockWise(),
                            -1 - count + index % area.width())
                        .relative(front, -1 - index / area.width());
                    assertEquals(expected, positions.get(index));
                    assertEquals(expected, area.blockAt(origin, front, index));
                    assertEquals(index / area.width(), area.row(index));
                    assertTrue(area.bounds(origin, front).contains(
                        net.minecraft.world.phys.Vec3.atCenterOf(expected)));
                }
            }
        }
    }

    @Test
    void nonAgriculturalProvidersShareGrowthWithoutChangingHeight() {
        WorkingArea.Provider centered = new WorkingArea.Provider() {
            public WorkingArea baseWorkingArea() {
                return new WorkingArea(-2, -1, -2, 2, 1, 2);
            }
            public int rangeUpgrades() {
                return 2;
            }
        };
        assertEquals(new WorkingArea(-4, -1, -4, 4, 1, 4),
            centered.workingArea());
        var front = new WorkingArea(-1, 2, 1, 1, 3, 3).expandHorizontal(1);
        assertEquals(new WorkingArea(-2, 2, 1, 2, 3, 5), front);
        assertEquals(50, front.size());
        assertEquals(new BlockPos(-2, 2, -1),
            front.blockAt(BlockPos.ZERO, Direction.NORTH, 0));
        assertEquals(new BlockPos(-2, 3, -1),
            front.blockAt(BlockPos.ZERO, Direction.NORTH, 25));
        assertEquals(new WorkingArea(1, 0, -2, 5, 0, 2),
            new WorkingArea(1, 0, -1, 3, 0, 1).expandHorizontal(1));
    }

    @Test
    void iterationKeepsImmutableCoordinatesAndRejectsInvalidIndexes() {
        var area = new WorkingArea(-1, 0, -3, 1, 0, -1);
        var origin = new BlockPos.MutableBlockPos(4, 5, 6);
        var blocks = area.blocks(origin, Direction.SOUTH);
        origin.set(100, 100, 100);
        var iterator = blocks.iterator();
        assertEquals(new BlockPos(5, 5, 5), iterator.next());
        while (iterator.hasNext())
            iterator.next();
        assertThrows(java.util.NoSuchElementException.class, iterator::next);
        assertThrows(IndexOutOfBoundsException.class,
            () -> area.blockAt(BlockPos.ZERO, Direction.NORTH, -1));
        assertThrows(IndexOutOfBoundsException.class,
            () -> area.row(area.size()));
        assertThrows(IllegalArgumentException.class,
            () -> area.blocks(BlockPos.ZERO, Direction.UP));
        assertEquals(-128, new WorkingArea(-128, 0, -128, -1, 0, -1)
            .expandHorizontal(9).minForward());
    }

    @Test
    void frontFieldRotatesWithoutIncludingTheControllerAndKeepsInclusiveSize() {
        var area = new WorkingArea(-4, 0, 1, 4, 0, 9);
        var origin = new BlockPos(10, 64, 20);
        assertEquals(new AABB(6, 64, 11, 15, 65, 20),
            area.bounds(origin, Direction.NORTH));
        assertEquals(new AABB(6, 64, 21, 15, 65, 30),
            area.bounds(origin, Direction.SOUTH));
        assertEquals(new AABB(11, 64, 16, 20, 65, 25),
            area.bounds(origin, Direction.EAST));
        assertEquals(new AABB(1, 64, 16, 10, 65, 25),
            area.bounds(origin, Direction.WEST));
    }
    @Test
    void rejectsReversedUnboundedAndVerticalAreas() {
        assertThrows(IllegalArgumentException.class,
            () -> new WorkingArea(2, 0, 0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class,
            () -> new WorkingArea(-129, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
            () -> new WorkingArea(0, 0, 0, 0, 0, 0).bounds(BlockPos.ZERO,
                Direction.UP));
    }
}
