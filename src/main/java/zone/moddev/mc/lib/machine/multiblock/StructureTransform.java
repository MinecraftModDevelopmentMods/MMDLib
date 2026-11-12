package zone.moddev.mc.lib.machine.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/** Coordinates are authored facing north; the controller occupies the origin. */
public record StructureTransform(BlockPos origin, Direction facing) {
    public StructureTransform {
        origin = origin.immutable();
        if (facing.getAxis().isVertical())
            throw new IllegalArgumentException("Horizontal facing required");
    }

    public Rotation rotation() {
        return switch (facing) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    public BlockPos position(BlockPos offset) {
        return origin.offset(offset.rotate(rotation()));
    }

    public Direction direction(Direction local) {
        return rotation().rotate(local);
    }

    public Direction local(Direction world) {
        for (var direction : Direction.values())
            if (direction(direction) == world)
                return direction;
        throw new IllegalArgumentException("Unknown direction");
    }

    public AABB bounds(ResolvedStructure structure) {
        AABB bounds = new AABB(origin);
        for (var member : structure.members())
            bounds = bounds.minmax(new AABB(position(member.offset())));
        return bounds;
    }
}
