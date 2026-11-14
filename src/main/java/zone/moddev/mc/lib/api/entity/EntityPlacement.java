package zone.moddev.mc.lib.api.entity;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side placement of detached single entities; payload ownership remains
 * with the caller.
 */
public final class EntityPlacement {
    public enum Result {
        SUCCESS, INVALID_ENTITY, DUPLICATE_UUID, NO_SPACE, SPAWN_REJECTED
    }

    public record Position(Vec3 location, float yaw) {
        public Position {
            if (!Double.isFinite(location.x) || !Double.isFinite(location.y)
                || !Double.isFinite(location.z) || !Float.isFinite(yaw))
                throw new IllegalArgumentException(
                    "Placement coordinates must be finite");
        }
    }

    private EntityPlacement() {
    }

    /**
     * Checks loaded entities across dimensions; this does not scan unloaded
     * chunks.
     */
    public static boolean uuidAvailable(UUID uuid, ServerLevel level) {
        for (var dimension : level.getServer().getAllLevels())
            if (dimension.getEntity(uuid) != null)
                return false;
        return true;
    }

    public static List<Position> around(Entity entity, BlockPos origin,
        List<Direction> sides) {
        double distance = Math.max(1, .55 + entity.getBbWidth() / 2.0);
        return sides.stream().map(side -> {
            if (side.getAxis().isVertical())
                throw new IllegalArgumentException(
                    "Release directions must be horizontal");
            return new Position(
                new Vec3(origin.getX() + .5 + side.getStepX() * distance,
                    origin.getY(),
                    origin.getZ() + .5 + side.getStepZ() * distance),
                side.toYRot());
        }).toList();
    }

    public static Result place(Entity entity, ServerLevel level,
        List<Position> candidates) {
        if (entity == null || entity.level() != level || entity.isRemoved()
            || entity.isPassenger() || entity.isVehicle()
            || !Float.isFinite(entity.getBbWidth())
            || !Float.isFinite(entity.getBbHeight()) || entity.getBbWidth() <= 0
            || entity.getBbHeight() <= 0)
            return Result.INVALID_ENTITY;
        if (!uuidAvailable(entity.getUUID(), level))
            return Result.DUPLICATE_UUID;
        var previous = entity.position();
        float yaw = entity.getYRot(), pitch = entity.getXRot();
        var movement = entity.getDeltaMovement();
        boolean spawned = false;
        try {
            for (var candidate : candidates) {
                var p = candidate.location();
                entity.moveTo(p.x, p.y, p.z, candidate.yaw(), 0);
                entity.setDeltaMovement(Vec3.ZERO);
                var box = entity.getBoundingBox();
                if (!level.hasChunksAt(
                    BlockPos.containing(box.minX, box.minY, box.minZ),
                    BlockPos.containing(box.maxX, box.maxY, box.maxZ))
                    || !level.getWorldBorder().isWithinBounds(box)
                    || !level.noCollision(entity))
                    continue;
                spawned = level.addFreshEntity(entity);
                return spawned ? Result.SUCCESS : Result.SPAWN_REJECTED;
            }
            return Result.NO_SPACE;
        } finally {
            if (!spawned) {
                entity.moveTo(previous.x, previous.y, previous.z, yaw, pitch);
                entity.setDeltaMovement(movement);
            }
        }
    }
}
