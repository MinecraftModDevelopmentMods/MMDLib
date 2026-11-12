package zone.moddev.mc.lib.machine.neoforge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import zone.moddev.mc.lib.machine.presentation.VisualCondition;
import zone.moddev.mc.lib.machine.presentation.VisualVariables;

/**
 * Tracking-player snapshots for static appearance, independent of open menus
 * and saved storage.
 */
public final class MachineVisualState {
    public static final ModelProperty<Map<String, Object>> VARIABLES = new ModelProperty<>();
    private final VisualVariables variables;
    private final Map<String, Object> defaults;
    private final List<VisualCondition> conditions;
    private final Set<String> referenced;
    private Map<String, Object> lastSent = Map.of();
    private Map<String, Object> clientSnapshot;
    private volatile ModelData modelData;
    private final Set<String> dirty = new java.util.HashSet<>();
    private Map<String, Object> serverSnapshot = Map.of();
    private Runnable unsubscribe;
    private Runnable schedule;
    private boolean scheduled;

    public MachineVisualState(VisualVariables variables,
        List<String> conditions) {
        this.variables = variables;
        defaults = variables.defaults();
        this.conditions = conditions.stream()
            .map(text -> VisualCondition.compile(text, defaults)).toList();
        referenced = this.conditions.stream()
            .flatMap(condition -> condition.variables().stream())
            .collect(Collectors.toUnmodifiableSet());
        clientSnapshot = defaults;
        modelData = ModelData.builder().with(VARIABLES, clientSnapshot).build();
        dirty.addAll(referenced);
        unsubscribe = variables.subscribe(referenced, this::invalidate);
    }

    /**
     * Bind a one-shot scheduler when the owner loads; no repeating tick
     * callback is needed.
     */
    public void start(Runnable schedule) {
        this.schedule = schedule;
        if (unsubscribe == null)
            unsubscribe = variables.subscribe(referenced, this::invalidate);
        dirty.addAll(referenced);
        requestUpdate();
    }

    /**
     * Disconnect component listeners on removal/unload. start() can reconnect a
     * restored owner.
     */
    public void stop() {
        if (unsubscribe != null)
            unsubscribe.run();
        unsubscribe = null;
        schedule = null;
        scheduled = false;
    }

    private void invalidate(String name) {
        dirty.add(name);
        requestUpdate();
    }

    private void requestUpdate() {
        if (!referenced.isEmpty() && schedule != null && !scheduled) {
            scheduled = true;
            schedule.run();
        }
    }

    private Map<String, Object> snapshot() {
        if (!dirty.isEmpty()) {
            var next = new HashMap<>(serverSnapshot);
            next.putAll(variables.snapshot(Set.copyOf(dirty)));
            dirty.clear();
            serverSnapshot = Map.copyOf(next);
        }
        return serverSnapshot;
    }

    /**
     * Called by the scheduled callback, after component mutations have settled.
     */
    public void flush(BlockEntity entity) {
        if (entity.isRemoved() || entity.getLevel() == null
            || entity.getLevel().isClientSide)
            return;
        flush(() -> FeatureSync.send(entity));
    }

    /**
     * Flush a coalesced invalidation through the owner's broadcast transport.
     */
    public void flush(Runnable broadcast) {
        scheduled = false;
        var current = snapshot();
        if (!current.equals(lastSent)) {
            lastSent = current;
            broadcast.run();
        }
    }

    /**
     * A full initial snapshot, also used for subsequent updates. Never
     * persisted to disk.
     */
    public CompoundTag write() {
        // Initial chunk data may be requested for just one newly tracking
        // player. Reading it
        // must not acknowledge a broadcast and suppress updates to the other
        // tracking players.
        var snapshot = snapshot();
        var tag = new CompoundTag();
        snapshot.forEach((name, value) -> {
            switch (VisualVariables.Kind.of(value)) {
                case BOOLEAN -> tag.putBoolean(name, (Boolean) value);
                case NUMBER ->
                    tag.putDouble(name, ((Number) value).doubleValue());
                case TEXT -> tag.putString(name, (String) value);
            }
        });
        return tag;
    }

    public void read(CompoundTag tag, BlockEntity entity) {
        if (apply(tag)) {
            entity.requestModelDataUpdate();
            var level = entity.getLevel();
            if (level != null && level.isClientSide)
                level.sendBlockUpdated(entity.getBlockPos(),
                    entity.getBlockState(), entity.getBlockState(),
                    Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Apply a detached client snapshot; return whether static geometry needs
     * refreshing.
     */
    public boolean apply(CompoundTag tag) {
        var values = new HashMap<>(defaults);
        for (String name : referenced) {
            Object value = switch (VisualVariables.Kind
                .of(defaults.get(name))) {
                case BOOLEAN -> tag.contains(name, Tag.TAG_BYTE)
                    ? tag.getBoolean(name)
                    : defaults.get(name);
                case NUMBER -> tag.contains(name, Tag.TAG_DOUBLE)
                    && Double.isFinite(tag.getDouble(name))
                        ? tag.getDouble(name)
                        : defaults.get(name);
                case TEXT -> tag.contains(name, Tag.TAG_STRING)
                    && tag.getString(name).length() <= 256
                        ? tag.getString(name)
                        : defaults.get(name);
            };
            values.put(name, value);
        }
        var next = Map.copyOf(values);
        if (next.equals(clientSnapshot))
            return false;
        boolean changed = conditions.stream().anyMatch(condition -> condition
            .test(clientSnapshot) != condition.test(next));
        clientSnapshot = next;
        modelData = ModelData.builder().with(VARIABLES, next).build();
        return changed;
    }

    /** Immutable data only; safe to read from chunk rendering threads. */
    public ModelData modelData() {
        return modelData;
    }
}
