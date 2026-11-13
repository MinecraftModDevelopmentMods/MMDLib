package zone.moddev.mc.lib.machine.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.api.entity.EntitySnapshot;

class VariableDeclarationTest {
    @Test
    void namedOptionsKeepPrivateDataPrivateAndValidateBounds() {
        var state = new ProcessorState();
        var cooldown = state.integer("cooldown", 0, 40, 0).define();
        var occupant = state.entity("occupant").synced().portable().define();
        var occupied = state.bool("occupied", false).visual()
            .derived(List.of(occupant), s -> !s.get(occupant).isEmpty());
        assertFalse(state.clientDefaults().containsKey("cooldown"));
        assertEquals(EntitySnapshot.EMPTY, state.clientValue("occupant"));
        assertTrue(occupied.visual());
        assertTrue(occupied.synchronize());
        assertFalse(occupant.visual());
        assertTrue(state.savePortable().contains("occupant"));
        assertFalse(state.savePortable().contains("occupied"));
        assertThrows(IllegalStateException.class,
            () -> state.update(() -> state.set(cooldown, 41)));
        assertThrows(IllegalArgumentException.class,
            () -> state.bool("bad", false).portable().derived(List.of(),
                ignored -> false));
    }
}
