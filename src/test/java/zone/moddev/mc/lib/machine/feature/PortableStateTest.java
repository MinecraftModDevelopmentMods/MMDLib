package zone.moddev.mc.lib.machine.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.Test;

class PortableStateTest {
    @Test
    void actionDeclarationsAreBuiltOncePerProcessor() {
        var builds = new java.util.concurrent.atomic.AtomicInteger();
        var processor = new PortableStoredProcessor() {
            protected java.util.Map<net.minecraft.resources.ResourceLocation, ProcessorAction> customActions() {
                builds.incrementAndGet();
                return java.util.Map.of();
            }
            public boolean canStart(ProcessorState state,
                ProcessorMachine machine) {
                return false;
            }
            public ProcessState process(ProcessorState state,
                ProcessorMachine machine, int deltaTicks) {
                return ProcessState.IDLE;
            }
        };
        org.junit.jupiter.api.Assertions.assertSame(processor.actions(),
            processor.actions());
        assertEquals(1, builds.get());
        assertTrue(
            processor.actions().containsKey(PortableStoredProcessor.PICKUP));
    }

    @Test
    void onlySelectedVariablesTravelAndInvalidFieldsUseDefaults() {
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
        state.loadPortable(payload);
        assertEquals("pig", state.get(animal));
        assertEquals(0, state.get(count));
        payload.putInt("animal", 42);
        payload.putInt("count", 3);
        state.loadPortable(payload);
        assertEquals("", state.get(animal));
        assertEquals(3, state.get(count));
        state.loadPortable(new CompoundTag());
        assertFalse(state.hasPortableContent());
    }
}
