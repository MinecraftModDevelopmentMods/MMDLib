package zone.moddev.mc.lib.gui.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.world.level.block.entity.BlockEntity;

import zone.moddev.mc.lib.MMDLib;
import zone.moddev.mc.lib.gui.load.ScreenBinder;

/**
 * Weak, per-machine tick cache shared by all viewers. Does not retain a machine through a value.
 */
final class ScreenSnapshots {
    private record Entry(long tick, ScreenBinder.Bound screen, Map<Integer, Object> values) {
    }

    private static final Map<BlockEntity, Entry> CACHE = new WeakHashMap<>();
    private static final Set<String> FAILURES = new HashSet<>();

    static void invalidate(BlockEntity entity) {
        CACHE.remove(entity);
    }

    static Map<Integer, Object> read(BlockEntity entity, ScreenBinder.Bound bound) {
        long tick = entity.getLevel().getGameTime();
        var old = CACHE.get(entity);
        if (old != null && old.tick() == tick && old.screen() == bound)
            return old.values();
        var result = new HashMap<Integer, Object>();
        var reads = new HashMap<zone.moddev.mc.lib.gui.model.ValueRef, Object>();
        var elements = bound.screen().elements();
        for (int i = 0; i < elements.size(); i++) {
            var e = elements.get(i);
            if (entity instanceof ScreenHost host) {
                var snapshot = e.snapshot(host);
                if (snapshot.isPresent()) {
                    result.put(
                        i * 2,
                        ScreenValues.snapshot(snapshot.get().value(),
                            entity.getLevel().registryAccess()));
                    result.put(
                        i * 2 + 1,
                        ScreenValues.snapshot(snapshot.get().maximum(),
                            entity.getLevel().registryAccess()));
                }
            }
            for (int max = 0; max < 2; max++) {
                var getter = (max == 0 ? bound.values() : bound.maxima()).get(e);
                if (getter == null)
                    continue;
                Object value = reads.get(getter.ref());
                try {
                    if (!reads.containsKey(getter.ref()))
                        value = getter.snapshot(entity, entity.getLevel().registryAccess());
                } catch (Throwable ex) {
                    if (ex instanceof VirtualMachineError error)
                        throw error;
                    if (ex instanceof ThreadDeath death)
                        throw death;
                    String key = bound.screen().source() + ":" + getter.ref().text();
                    if (FAILURES.add(key))
                        MMDLib.LOGGER.error("GUI getter failed: {}", key, ex);
                }
                reads.put(getter.ref(), value);
                result.put(i * 2 + max, value);
            }
        }
        CACHE.put(entity, new Entry(tick, bound, result));
        return result;
    }
}
