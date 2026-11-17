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
     * Never banks more than a fractional tick or accelerates a paused
     * processor.
     */
    public int steps(Processor<?> processor, FeatureHost host) {
        if (processor.paused())
            return 1;
        int count = Math.clamp(gearboxes.getAsInt(), 0, 8);
        if (count == 0)
            return 1;
        int previous = fractions.getOrDefault(processor.key(), 0);
        int total = previous + count;
        int remainder = total % 8;
        if (remainder != previous) {
            fractions.put(processor.key(), remainder);
            host.markChanged();
        }
        return 1 + total / 8;
    }
}
