package zone.moddev.mc.lib.gui.runtime;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;

import zone.moddev.mc.lib.gui.value.ValueTypeRegistry;

/**
 * Registry-backed value snapshots, deltas and transport shared by all elements.
 */
public final class ScreenValues {
    private ScreenValues() {
    }

    public static Object snapshot(Object value, RegistryAccess registries) {
        return ValueTypeRegistry.GLOBAL.snapshot(value, registries);
    }

    public static Object snapshot(Object value) {
        return snapshot(value, RegistryAccess.EMPTY);
    }

    public static boolean equal(Object a, Object b) {
        return ValueTypeRegistry.GLOBAL.equal(a, b);
    }

    public static Map<Integer, Object> changed(Map<Integer, Object> previous,
        Map<Integer, Object> current, boolean initial) {
        var delta = new HashMap<Integer, Object>();
        current.forEach((key, value) -> {
            if (initial || !previous.containsKey(key)
                || !equal(previous.get(key), value))
                delta.put(key, value);
        });
        return delta;
    }

    public static void write(RegistryFriendlyByteBuf buf, Object value) {
        ValueTypeRegistry.GLOBAL.write(buf, value);
    }

    public static Object read(RegistryFriendlyByteBuf buf) {
        return ValueTypeRegistry.GLOBAL.read(buf);
    }
}
