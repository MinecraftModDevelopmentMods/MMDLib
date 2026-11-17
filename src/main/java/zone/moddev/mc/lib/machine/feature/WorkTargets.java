package zone.moddev.mc.lib.machine.feature;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import net.minecraft.core.BlockPos;

/**
 * Transient active blocks, synchronized to tracking clients independently of
 * menus.
 */
public final class WorkTargets implements MachineFeature<List<BlockPos>> {
    public static final int MAX_TARGETS = 256;
    public static final Codec<List<BlockPos>> CODEC = BlockPos.CODEC.listOf()
        .validate(positions -> positions.size() <= MAX_TARGETS
            ? DataResult.success(positions)
            : DataResult.error(() -> "Too many work targets"));

    /**
     * Implement on a processor; positions are absolute world block coordinates.
     */
    public interface Provider {
        List<BlockPos> workingBlocks();
    }

    private final String key;
    private final Supplier<List<BlockPos>> source;
    private final BooleanSupplier active;
    private List<BlockPos> targets = List.of();

    public WorkTargets(String key, Supplier<List<BlockPos>> source,
        BooleanSupplier active) {
        this.key = Objects.requireNonNull(key);
        this.source = Objects.requireNonNull(source);
        this.active = Objects.requireNonNull(active);
    }

    public String key() {
        return key;
    }
    public Codec<List<BlockPos>> codec() {
        return CODEC;
    }
    public List<BlockPos> targets() {
        return targets;
    }
    // Work is transient. Never restore last session's particles from disk.
    public List<BlockPos> state() {
        return List.of();
    }
    public void restore(List<BlockPos> ignored) {
        reset();
    }
    public void reset() {
        targets = List.of();
    }
    public boolean syncsToClient() {
        return true;
    }
    public List<BlockPos> clientState() {
        return targets;
    }
    public void restoreOnClient(List<BlockPos> positions) {
        targets = snapshot(positions);
    }

    public void tick(FeatureHost host) {
        update(host,
            active.getAsBoolean() ? snapshot(source.get()) : List.of());
    }

    public void suspended(FeatureHost host) {
        update(host, List.of());
    }

    private void update(FeatureHost host, List<BlockPos> next) {
        if (targets.equals(next))
            return;
        targets = next;
        host.markSync();
    }

    private static List<BlockPos> snapshot(List<BlockPos> positions) {
        var unique = new LinkedHashSet<BlockPos>();
        for (var position : positions) {
            unique.add(Objects.requireNonNull(position).immutable());
            if (unique.size() == MAX_TARGETS)
                break;
        }
        return List.copyOf(unique);
    }
}
