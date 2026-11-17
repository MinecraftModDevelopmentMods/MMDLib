package zone.moddev.mc.lib.machine.feature;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntSupplier;

import com.mojang.serialization.Codec;

/** Saved fractional work ticks, shared by every processor implementation. */
public final class ProcessorClock
    implements MachineFeature<Map<String, Integer>> {
    private static final Codec<Map<String, Integer>> CODEC = Codec
        .unboundedMap(Codec.STRING, Codec.intRange(0, 7));
    private final IntSupplier gearboxes;
    private final Map<String, Integer> fractions = new HashMap<>();

    public ProcessorClock(IntSupplier gearboxes) {
        this.gearboxes = java.util.Objects.requireNonNull(gearboxes);
    }

    public String key() {
        return "mmdlib:processor_clock";
    }
    public Codec<Map<String, Integer>> codec() {
        return CODEC;
    }
    public Map<String, Integer> state() {
        return Map.copyOf(fractions);
    }
    public void restore(Map<String, Integer> state) {
        fractions.clear();
        fractions.putAll(state);
    }

    /**
     * Schedules a batch, committing fractional work only when it runs. Idle,
     * stalled, held and paused processors never bank work or dirty saves.
     */
    public void tick(Processor<?> processor, FeatureHost host) {
        int count = processor.paused()
            ? 0
            : Math.clamp(gearboxes.getAsInt(), 0, 8);
        int previous = fractions.getOrDefault(processor.key(), 0);
        int total = previous + count;
        processor.tick(host, 1 + total / 8);
        int remainder = total % 8;
        if (processor.processing() && remainder != previous) {
            fractions.put(processor.key(), remainder);
            host.markChanged();
        }
    }
}
