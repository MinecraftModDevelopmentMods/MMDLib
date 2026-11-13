package zone.moddev.mc.lib.machine.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.Test;

class PortableStateTest {
    @Test
    void onlySelectedVariablesTravelAndInvalidPayloadsDoNotPartiallyRestore() {
        var state = new ProcessorState();
        var animal = state.portable(state.define("animal", Codec.STRING, ""));
        var count = state
            .portable(state.define("count", Codec.intRange(0, 4), 0));
        var cooldown = state.define("cooldown", Codec.INT, 0);
        state.update(() -> {
            state.set(animal, "cow");
            state.set(count, 1);
            state.set(cooldown, 40);
        });
        var payload = state.savePortable();
        assertFalse(payload.contains("cooldown"));
        assertEquals("", state.portableDefaults().getString("animal"));
        assertTrue(state.hasPortableContent());
        state.clearPortable();
        assertFalse(state.hasPortableContent());
        assertEquals(40, state.get(cooldown));
        state.loadPortable(payload);
        assertEquals("cow", state.get(animal));
        payload.putString("animal", "pig");
        payload.putInt("count", -1);
        assertThrows(IllegalStateException.class,
            () -> state.loadPortable(payload));
        assertEquals("cow", state.get(animal));
        state.loadPortable(new CompoundTag());
        assertFalse(state.hasPortableContent());
    }
}
