package zone.moddev.mc.lib.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.concurrent.Executors;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.api.util.ChangeSignal;
import zone.moddev.mc.lib.machine.definition.MachineBehavior;
import zone.moddev.mc.lib.machine.neoforge.MachineShapeState;
import zone.moddev.mc.lib.machine.presentation.VisualVariables;

class MachineShapeStateTest {
    @Test
    void sharedGraphNotifiesShapesOnlyWhenSelectionChanges() {
        int[] count = {0};
        var changes = new ChangeSignal();
        var variables = new VisualVariables.Builder().observedBy(changes)
            .add("processors.test.count", 0, () -> count[0]).build();
        var host = new zone.moddev.mc.lib.machine.feature.FeatureHost(() -> {
        });
        host.bindVisualVariables(() -> variables);
        var notifications = new java.util.concurrent.atomic.AtomicInteger();
        var state = new MachineShapeState(
            List.of(new MachineBehavior.Shape("plate",
                "$processors.test.count > 0", 0, 0, 0, 16, 3, 16)),
            variables, notifications::incrementAndGet);
        org.junit.jupiter.api.Assertions
            .assertFalse(host.condition("$processors.test.count > 0"));
        count[0] = 1;
        changes.changed();
        assertEquals(1, notifications.get());
        assertEquals(3.0 / 16,
            state.shape(Direction.NORTH).bounds().getYsize());
        org.junit.jupiter.api.Assertions
            .assertTrue(host.condition("$processors.test.count > 0"));
        count[0] = 2;
        changes.changed();
        assertEquals(1, notifications.get(),
            "Value changes within the same shape stay batched");
        count[0] = 0;
        changes.changed();
        assertEquals(2, notifications.get());
        assertSame(Shapes.block(), state.shape(Direction.NORTH));
    }
    @Test
    void inactiveShapesRemainTargetableAndUpdatesPublishRotatedGeometry()
        throws Exception {
        boolean[] active = {false};
        var changed = new ChangeSignal();
        var variables = new VisualVariables.Builder().observedBy(changed)
            .add("active", false, () -> active[0]).build();
        var state = new MachineShapeState(List.of(
            new MachineBehavior.Shape("half", "$active", 0, 0, 0, 8, 16, 16)),
            variables);
        assertSame(Shapes.block(), state.shape(Direction.NORTH));
        // The worker reads only published geometry while the owner changes
        // state.
        try (var executor = Executors.newSingleThreadExecutor()) {
            var reads = executor.submit(() -> {
                for (int i = 0; i < 10000; i++) {
                    var shape = state.shape(Direction.NORTH);
                    double width = shape.bounds().getXsize();
                    if (width != .5 && width != 1)
                        throw new AssertionError("Partial shape publication");
                }
            });
            for (int i = 0; i < 100; i++) {
                active[0] = !active[0];
                changed.changed();
            }
            reads.get();
        }
        active[0] = true;
        changed.changed();
        assertEquals(.5, state.shape(Direction.NORTH).bounds().getXsize());
        assertEquals(.5, state.shape(Direction.EAST).bounds().getZsize());
        assertEquals(.5, state.shape(Direction.SOUTH).bounds().minX);
        active[0] = false;
        changed.changed();
        assertSame(Shapes.block(), state.shape(Direction.WEST));
    }
}
