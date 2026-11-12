package zone.moddev.mc.lib.machine.multiblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

class StructureTransformTest {
    @Test
    void rotatesPositionsAndFacesTogether() {
        var origin = new BlockPos(-17, 64, 31);
        for (var facing : Direction.Plane.HORIZONTAL) {
            var transform = new StructureTransform(origin, facing);
            assertEquals(origin.relative(facing, 3).above(2),
                transform.position(new BlockPos(0, 2, -3)));
            for (var local : Direction.values())
                assertEquals(local,
                    transform.local(transform.direction(local)));
        }
        assertThrows(IllegalArgumentException.class,
            () -> new StructureTransform(origin, Direction.UP));
    }

    @Test
    void linksCopyMutableCoordinatesAndRoundTripIdentity() {
        var mutable = new BlockPos.MutableBlockPos(1, 2, 3);
        var link = new MemberLink(ResourceLocation.parse("minecraft:overworld"),
            mutable, UUID.randomUUID(), "belt/0");
        mutable.set(9, 9, 9);
        assertEquals(new BlockPos(1, 2, 3), link.controller());
        assertEquals(link, MemberLink.load(link.save()));
        assertThrows(IllegalArgumentException.class,
            () -> new MemberLink(link.dimension(), BlockPos.ZERO,
                link.formation(), ""));
    }
}
