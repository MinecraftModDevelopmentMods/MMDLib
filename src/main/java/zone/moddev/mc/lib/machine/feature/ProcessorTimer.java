package zone.moddev.mc.lib.machine.feature;

/**
 * Persisted countdown advanced by the processor lifecycle, never by wall-clock
 * time.
 */
public final class ProcessorTimer {
    public enum Policy {
        IDLE, ACTIVE, UNPAUSED, ALWAYS;

        boolean advances(Processor.ProcessState state) {
            return switch (this) {
                case IDLE -> state == Processor.ProcessState.IDLE;
                case ACTIVE -> state != Processor.ProcessState.IDLE
                    && state != Processor.ProcessState.PAUSED
                    && state != Processor.ProcessState.HELD;
                case UNPAUSED -> state != Processor.ProcessState.PAUSED
                    && state != Processor.ProcessState.HELD;
                case ALWAYS -> true;
            };
        }
    }

    private final ProcessorState state;
    private final ProcessorState.Key<Integer> key;
    private final Policy policy;
    private final int maximum;

    ProcessorTimer(ProcessorState state, String name, int maximum,
        Policy policy) {
        if (maximum < 1)
            throw new IllegalArgumentException(
                "Timer maximum must be positive");
        this.state = state;
        this.maximum = maximum;
        this.policy = java.util.Objects.requireNonNull(policy);
        key = state.integer(name, 0, maximum, 0).define();
    }

    public ProcessorState.Key<Integer> key() {
        return key;
    }
    public int remaining() {
        return state.get(key);
    }
    public boolean ready() {
        return remaining() == 0;
    }
    public void start(int ticks) {
        if (ticks < 0 || ticks > maximum)
            throw new IllegalArgumentException("Timer " + key.name()
                + " must start within 0.." + maximum + " ticks, got " + ticks);
        state.update(() -> state.set(key, ticks));
    }
    public void clear() {
        start(0);
    }

    void advance(int deltaTicks, Processor.ProcessState phase) {
        if (deltaTicks < 0)
            throw new IllegalArgumentException(
                "Timer delta cannot be negative");
        if (policy.advances(phase) && !ready())
            state.set(key, Math.max(0, remaining() - deltaTicks));
    }
}
