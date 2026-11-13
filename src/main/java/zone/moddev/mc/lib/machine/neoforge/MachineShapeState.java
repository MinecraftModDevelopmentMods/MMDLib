package zone.moddev.mc.lib.machine.neoforge;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import zone.moddev.mc.lib.machine.definition.MachineBehavior;
import zone.moddev.mc.lib.machine.presentation.VisualCondition;
import zone.moddev.mc.lib.machine.presentation.VisualVariables;

/**
 * Owner-thread condition sampling, with immutable geometry for render workers.
 */
public final class MachineShapeState {
    private record Part(VisualCondition condition,
        Map<Direction, VoxelShape> shapes) {
    }

    private final VisualVariables variables;
    private final List<Part> parts;
    private final Runnable changed;
    private List<Part> active = List.of();
    private volatile Map<Direction, VoxelShape> snapshot = Map.of();

    public MachineShapeState(List<MachineBehavior.Shape> shapes,
        VisualVariables variables) {
        this(shapes, variables, () -> {
        });
    }

    public MachineShapeState(List<MachineBehavior.Shape> shapes,
        VisualVariables variables, Runnable changed) {
        this.variables = variables;
        this.changed = changed;
        var defaults = variables.defaults();
        parts = shapes.stream()
            .map(shape -> new Part(
                VisualCondition.compile(shape.when(), defaults), rotate(shape)))
            .toList();
        var referenced = parts.stream()
            .flatMap(part -> part.condition().variables().stream())
            .collect(Collectors.toUnmodifiableSet());
        variables.subscribe(referenced, ignored -> refresh());
        refresh();
    }

    /**
     * Called only by the owning thread, including after client state restore.
     */
    public void refresh() {
        var selected = parts.stream()
            .filter(part -> part.condition()
                .test(variables.snapshot(part.condition().variables())))
            .toList();
        if (selected.equals(active))
            return;
        var next = new EnumMap<Direction, VoxelShape>(Direction.class);
        for (var part : selected) {
            part.shapes().forEach(
                (facing, shape) -> next.merge(facing, shape, Shapes::or));
        }
        active = selected;
        snapshot = Map.copyOf(next);
        changed.run();
    }

    /** Pure read: a machine always retains a targetable fallback. */
    public VoxelShape shape(Direction facing) {
        return snapshot.getOrDefault(facing, Shapes.block());
    }

    private static Map<Direction, VoxelShape> rotate(
        MachineBehavior.Shape shape) {
        var result = new EnumMap<Direction, VoxelShape>(Direction.class);
        double x1 = shape.x1(), z1 = shape.z1(), x2 = shape.x2(),
            z2 = shape.z2();
        for (var facing : List.of(Direction.NORTH, Direction.EAST,
            Direction.SOUTH, Direction.WEST)) {
            result.put(facing, Shapes.box(x1 / 16, shape.y1() / 16, z1 / 16,
                x2 / 16, shape.y2() / 16, z2 / 16));
            double oldX1 = x1, oldX2 = x2;
            x1 = 16 - z2;
            x2 = 16 - z1;
            z1 = oldX1;
            z2 = oldX2;
        }
        return Map.copyOf(result);
    }
}
