package zone.moddev.mc.lib.machine.definition;

import java.util.EnumSet;
import java.util.Set;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Allowed fronts and the independent placement preference. */
public record MachineFacing(Set<Direction> directions, boolean towardPlayer) {
    public static final MachineFacing DEFAULT = parse("horizontal",
        "toward-player");

    public MachineFacing {
        directions = Set.copyOf(directions);
        if (directions.isEmpty())
            throw new IllegalArgumentException(
                "Facing requires at least one direction");
    }

    public static MachineFacing parse(String faces, String placement) {
        var directions = EnumSet.noneOf(Direction.class);
        if (faces.equals("horizontal"))
            directions.addAll(Set.of(Direction.NORTH, Direction.SOUTH,
                Direction.EAST, Direction.WEST));
        else if (faces.equals("all"))
            directions = EnumSet.allOf(Direction.class);
        else
            for (var name : faces.trim().split("\\s+")) {
                var direction = Direction.byName(name);
                if (direction == null || !directions.add(direction))
                    throw new IllegalArgumentException(
                        "Invalid or duplicate facing: " + name);
            }
        if (!placement.equals("toward-player")
            && !placement.equals("away-from-player"))
            throw new IllegalArgumentException(
                "Invalid placement-facing: " + placement);
        return new MachineFacing(directions, placement.equals("toward-player"));
    }

    public boolean horizontal() {
        return directions.stream().noneMatch(d -> d.getAxis().isVertical());
    }

    public Direction defaultDirection() {
        return directions.contains(Direction.NORTH)
            ? Direction.NORTH
            : java.util.Arrays.stream(Direction.values())
                .filter(directions::contains).findFirst().orElseThrow();
    }

    /**
     * Ordered nearest-look directions, as supplied by the placement context.
     */
    public Direction placement(Direction[] nearest) {
        for (var direction : nearest) {
            var front = towardPlayer ? direction.getOpposite() : direction;
            if (directions.contains(front))
                return front;
        }
        return defaultDirection();
    }

    public Direction transformed(Direction current, Direction proposed) {
        return directions.contains(proposed) ? proposed : current;
    }

    /**
     * Rotates north-authored block coordinates; vertical fronts have north as
     * top.
     */
    public static Vec3 point(Direction front, double x, double y, double z) {
        return switch (front) {
            case NORTH -> new Vec3(x, y, z);
            case EAST -> new Vec3(1 - z, y, x);
            case SOUTH -> new Vec3(1 - x, y, 1 - z);
            case WEST -> new Vec3(z, y, 1 - x);
            case UP -> new Vec3(1 - x, 1 - z, 1 - y);
            case DOWN -> new Vec3(x, z, 1 - y);
        };
    }

    /** Model JSON rotations, matching point(). */
    public static int modelX(Direction front) {
        return front == Direction.UP ? 270 : front == Direction.DOWN ? 90 : 0;
    }

    public static int modelY(Direction front) {
        return switch (front) {
            case EAST -> 90;
            case SOUTH, UP -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }
}
