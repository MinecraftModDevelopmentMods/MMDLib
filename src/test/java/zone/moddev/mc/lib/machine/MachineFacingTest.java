package zone.moddev.mc.lib.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.machine.definition.MachineFacing;
import zone.moddev.mc.lib.machine.feature.WorkingArea;

class MachineFacingTest {
    @Test
    void allSubsetsConstrainPlacementAndTransforms() {
        var directions = Direction.values();
        for (int mask = 1; mask < 64; mask++) {
            var allowed = EnumSet.noneOf(Direction.class);
            for (int i = 0; i < 6; i++)
                if ((mask & (1 << i)) != 0)
                    allowed.add(directions[i]);
            for (boolean toward : new boolean[]{false, true}) {
                var policy = new MachineFacing(allowed, toward);
                assertTrue(allowed.contains(policy.defaultDirection()));
                for (var first : directions) {
                    var order = new java.util.ArrayList<Direction>();
                    order.add(first);
                    for (var other : directions)
                        if (other != first)
                            order.add(other);
                    var selected = policy
                        .placement(order.toArray(Direction[]::new));
                    assertTrue(allowed.contains(selected));
                    var preferred = toward ? first.getOpposite() : first;
                    if (allowed.contains(preferred))
                        assertEquals(preferred, selected);
                    for (var next : directions)
                        assertTrue(allowed
                            .contains(policy.transformed(selected, next)));
                }
            }
        }
    }

    @Test
    void transformsAgreeWithFrontAndVerticalTop() {
        for (var front : Direction.values()) {
            var center = MachineFacing.point(front, .5, .5, .5);
            var face = MachineFacing.point(front, .5, .5, 0).subtract(center);
            assertEquals(Vec3.atLowerCornerOf(front.getNormal()).scale(.5),
                face);
            if (front.getAxis().isVertical())
                assertEquals(new Vec3(0, 0, -.5),
                    MachineFacing.point(front, .5, 1, .5).subtract(center));
            var area = new WorkingArea(-1, -2, 1, 2, 1, 3);
            var bounds = area.bounds(BlockPos.ZERO, front);
            var positions = new java.util.HashSet<BlockPos>();
            for (var position : area.blocks(BlockPos.ZERO, front)) {
                assertTrue(bounds.contains(Vec3.atCenterOf(position)));
                positions.add(position);
            }
            assertEquals(area.size(), positions.size());
        }
    }

    @Test
    void rejectsInvalidDeclarations() {
        for (var faces : new String[]{"", "up up", "horizontal up", "diagonal"})
            assertThrows(IllegalArgumentException.class,
                () -> MachineFacing.parse(faces, "toward-player"));
        assertThrows(IllegalArgumentException.class,
            () -> MachineFacing.parse("all", "sideways"));
    }
}
