package zone.moddev.mc.lib.machine.inventory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A machine's storages, grouped under the keys its recipes use. Recipes join on
 * keys: a recipe naming a key the machine lacks never matches it. Immutable
 * once built.
 *
 * <pre>{@code
 * var inventory = MachineInventory.builder()
 *     .group("input", StorageGroups.items(items, 0))
 *     .group("output", StorageGroups.items(items, 1, 2))
 *     .group("energy", StorageGroups.energy(buffer)).build();
 * }</pre>
 */
public final class MachineInventory {
    private final Map<String, SlotGroup> groups;

    private MachineInventory(Map<String, SlotGroup> groups) {
        this.groups = Collections.unmodifiableMap(new LinkedHashMap<>(groups));
    }

    /** A builder with no groups. */
    public static Builder builder() {
        return new Builder();
    }

    /** The group under {@code key}, if any. */
    public Optional<SlotGroup> group(String key) {
        return Optional.ofNullable(groups.get(key));
    }

    /** Every group key, in the order added. */
    public Set<String> keys() {
        return groups.keySet();
    }

    /** Starts a transaction for changes to these groups. */
    public MachineTx begin() {
        return new SnapshotTx();
    }

    /** Captures the groups' change counters for a failed recipe search. */
    public long[] revisions() {
        return groups.values().stream().mapToLong(SlotGroup::revision)
            .toArray();
    }

    /** Whether these groups still have the captured revisions. */
    public boolean unchanged(long[] revisions) {
        if (revisions.length != groups.size())
            return false;
        int index = 0;
        for (var group : groups.values())
            if (group.revision() != revisions[index++])
                return false;
        return true;
    }

    /** Collects groups by key. */
    public static final class Builder {
        private final Map<String, SlotGroup> groups = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * Adds {@code group} under {@code key}.
         *
         * @throws IllegalArgumentException
         *             if {@code key} is already used
         */
        public Builder group(String key, SlotGroup group) {
            if (groups.putIfAbsent(key, group) != null)
                throw new IllegalArgumentException(
                    "Duplicate machine group " + key);
            return this;
        }

        /** The inventory. */
        public MachineInventory build() {
            return new MachineInventory(groups);
        }
    }
}
