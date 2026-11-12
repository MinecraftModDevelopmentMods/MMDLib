package zone.moddev.mc.lib.gui.runtime;

/**
 * Request bookkeeping only; hosts own authorization, payloads, revisions and
 * recovery policy.
 */
public final class MenuRequests {
    private MenuRequests() {
    }

    /**
     * One outstanding client request, with a monotonically increasing positive
     * ID.
     */
    public static final class Client {
        private final int timeoutTicks;
        private int sequence, pending, ticks;

        public Client(int timeoutTicks) {
            if (timeoutTicks < 1)
                throw new IllegalArgumentException(
                    "Request timeout must be positive");
            this.timeoutTicks = timeoutTicks;
        }

        public boolean pending() {
            return pending != 0;
        }

        /**
         * Returns zero when busy or exhausted. IDs never wrap or get reused
         * after a timeout.
         */
        public int begin() {
            if (pending() || sequence == Integer.MAX_VALUE)
                return 0;
            pending = ++sequence;
            ticks = 0;
            return pending;
        }

        /** Exact acknowledgments do not advance the local sequence. */
        public boolean acknowledgeExact(int request) {
            if (request != sequence)
                return false;
            clear();
            return true;
        }

        /**
         * Cumulative snapshots may advance the sequence, including before the
         * first local request.
         */
        public boolean acknowledgeThrough(int request) {
            sequence = Math.max(sequence, request);
            if (request < pending)
                return false;
            clear();
            return true;
        }

        /**
         * Releases the pending request once; the host decides whether/how to
         * recover.
         */
        public boolean tick() {
            if (!pending() || ++ticks < timeoutTicks)
                return false;
            clear();
            return true;
        }

        private void clear() {
            pending = 0;
            ticks = 0;
        }
    }

    /**
     * Monotonic replay protection and a bounded per-tick allowance, after host
     * authorization.
     */
    public static final class Server {
        private final int perTick;
        private int lastRequest, actions;
        private long tick;
        private boolean hasTick;

        public Server(int perTick) {
            if (perTick < 1)
                throw new IllegalArgumentException(
                    "Request allowance must be positive");
            this.perTick = perTick;
        }

        /**
         * Consume fresh IDs even when later feature validation or the rate
         * check rejects the action.
         */
        public boolean receive(int request) {
            if (request <= lastRequest)
                return false;
            lastRequest = request;
            return true;
        }

        public int acknowledged() {
            return lastRequest;
        }

        /**
         * Call once per fresh request. Replays must not consume this allowance.
         */
        public boolean allow(long currentTick) {
            if (!hasTick || tick != currentTick) {
                hasTick = true;
                tick = currentTick;
                actions = 0;
            }
            if (actions >= perTick)
                return false;
            actions++;
            return true;
        }
    }
}
