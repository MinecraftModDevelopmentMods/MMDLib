package zone.moddev.mc.lib.api.redstone;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.ToDoubleFunction;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import zone.moddev.mc.lib.api.automation.ResourceGroup;
import zone.moddev.mc.lib.api.capabilities.RelativeFace;

/**
 * Persistent per-face signal intervals and outputs discovered from machine
 * parts.
 */
public final class RedstoneControl {
    public enum Mode {
        IGNORE, READ, EMIT
    }

    private static final Mode[] MODES = Mode.values();
    private static final RelativeFace[] FACES = RelativeFace.values();
    private final BlockEntity owner;
    private final List<RedstoneOutput> outputs;
    private final int[] modes = new int[6], incoming = new int[6],
        outgoing = new int[6], lower = new int[6], upper = new int[6];
    private final boolean[] outside = new boolean[6], inverted = new boolean[6];
    private final String[] sourceIds = new String[6];
    // The catalog is immutable. Resolve saved IDs only when loading or changing
    // a selection.
    private final int[] sourceIndexes = new int[6];
    private final int[] sampledLevels = new int[6];
    private long revision;
    // The first tick repairs only adjacent dust, without notifying unrelated
    // neighbours.
    private int connections = -1;
    private boolean paused, legacyAny;

    public RedstoneControl(BlockEntity owner, List<ResourceGroup<?>> groups,
        ToDoubleFunction<ResourceGroup<?>> fullness) {
        this(owner, groups.stream().map(group -> RedstoneOutput.resource(group,
            () -> fullness.applyAsDouble(group))).toList());
    }

    /**
     * Discover output-capable runtime parts; nonparticipating parts contribute
     * nothing.
     */
    public RedstoneControl(BlockEntity owner, Collection<?> parts) {
        this.owner = Objects.requireNonNull(owner);
        var found = new LinkedHashMap<String, RedstoneOutput>();
        for (var part : parts)
            if (part instanceof IRedstoneOutput provider)
                for (var output : provider.redstoneOutputs()) {
                    if (found.putIfAbsent(output.id(), output) != null)
                        throw new IllegalArgumentException(
                            "Duplicate redstone output ID: " + output.id());
                }
        if (found.size() > 4096)
            throw new IllegalArgumentException("Too many redstone outputs");
        outputs = List.copyOf(found.values());
        Arrays.fill(lower, 1);
        Arrays.fill(upper, 15);
        Arrays.fill(sourceIds, "");
        Arrays.fill(sourceIndexes, -1);
    }

    public List<RedstoneOutput> outputs() {
        return outputs;
    }

    public long revision() {
        return revision;
    }

    public Mode mode(int face) {
        return MODES[modes[face]];
    }

    public boolean outside(int face) {
        return outside[face];
    }

    public boolean inverted(int face) {
        return inverted[face];
    }

    public void inverted(int face, boolean value) {
        inverted[face] = value;
        changed();
    }

    public int lower(int face) {
        return lower[face];
    }

    public int upper(int face) {
        return upper[face];
    }

    public int incoming(int face) {
        return incoming[face];
    }

    public int outgoing(int face) {
        return outgoing[face];
    }

    public boolean paused() {
        return paused;
    }

    public int source(int face) {
        return sourceIndexes[face];
    }

    private int findSource(String id) {
        for (int i = 0; i < outputs.size(); i++)
            if (outputs.get(i).id().equals(id))
                return i;
        return -1;
    }

    public Direction world(int face) {
        return FACES[face].toWorld(RelativeFace.facing(owner.getBlockState()));
    }

    public void mode(int face, Mode mode) {
        modes[face] = mode.ordinal();
        changed();
    }

    public void outside(int face, boolean value) {
        outside[face] = value;
        changed();
    }

    public boolean range(int face, int low, int high) {
        if (low < 0 || high > 15 || low > high)
            return false;
        lower[face] = low;
        upper[face] = high;
        changed();
        return true;
    }

    public boolean source(int face, int index) {
        if (index < 0 || index >= outputs.size())
            return false;
        sourceIds[face] = outputs.get(index).id();
        sourceIndexes[face] = index;
        changed();
        return true;
    }

    private void changed() {
        legacyAny = false;
        revision++;
        owner.setChanged();
    }

    /** Both endpoints belong to Between; Outside is its exact complement. */
    public static boolean matches(int signal, int low, int high,
        boolean outside) {
        return (signal >= low && signal <= high) != outside;
    }

    /**
     * Convenience for hosts without stateful features; otherwise split around
     * feature ticks.
     */
    public void tick() {
        readInputs();
        updateOutputs();
    }

    /** Read and resolve processing gates before ticking machine features. */
    public void readInputs() {
        var level = owner.getLevel();
        if (level == null || level.isClientSide)
            return;
        paused = false;
        boolean powered = false;
        for (int face = 0; face < 6; face++) {
            var direction = world(face);
            var neighbour = owner.getBlockPos().relative(direction);
            incoming[face] = level.hasChunkAt(neighbour)
                ? Math.clamp(level.getSignal(neighbour, direction), 0, 15)
                : 0;
            powered |= incoming[face] > 0;
            if (mode(face) == Mode.READ)
                paused |= matches(incoming[face], lower[face], upper[face],
                    outside[face]);
        }
        if (legacyAny)
            paused = !powered;
    }

    /**
     * Evaluate selected suppliers once each after feature/transfer ticks, then
     * notify neighbours.
     */
    public void updateOutputs() {
        var level = owner.getLevel();
        if (level == null || level.isClientSide)
            return;
        boolean outputsChanged = false;
        int newConnections = 0;
        for (int face = 0; face < 6; face++) {
            int index = source(face);
            int output = 0;
            if (connects(face))
                newConnections |= 1 << world(face).ordinal();
            if (mode(face) == Mode.EMIT && index >= 0) {
                // At most six faces: reuse an earlier face's raw sample without
                // a per-tick map.
                int earlier = 0;
                while (earlier < face && (mode(earlier) != Mode.EMIT
                    || sourceIndexes[earlier] != index))
                    earlier++;
                output = sampledLevels[face] = earlier < face
                    ? sampledLevels[earlier]
                    : outputs.get(index).level();
                if (inverted[face])
                    output = 15 - output;
            }
            outputsChanged |= outgoing[face] != output;
            outgoing[face] = output;
        }
        if (connections != newConnections) {
            boolean firstTick = connections < 0;
            connections = newConnections;
            var state = owner.getBlockState();
            if (firstTick) {
                // Old saves may still have all-face wire geometry. Updating the
                // machine's
                // entire neighbourhood here would pulse observers on every
                // chunk load.
                for (var direction : Direction.Plane.HORIZONTAL) {
                    var neighbour = owner.getBlockPos().relative(direction);
                    if (!level.hasChunkAt(neighbour))
                        continue;
                    var wire = level.getBlockState(neighbour);
                    if (wire.is(Blocks.REDSTONE_WIRE)) {
                        var updated = wire.updateShape(direction.getOpposite(),
                            state, level, neighbour, owner.getBlockPos());
                        Block.updateOrDestroy(wire, updated, level, neighbour,
                            Block.UPDATE_ALL);
                    }
                }
            } else {
                level.sendBlockUpdated(owner.getBlockPos(), state, state,
                    Block.UPDATE_CLIENTS);
                state.updateNeighbourShapes(level, owner.getBlockPos(),
                    Block.UPDATE_ALL);
                outputsChanged = true;
            }
        }
        if (outputsChanged)
            level.updateNeighborsAt(owner.getBlockPos(),
                owner.getBlockState().getBlock());
    }

    public static int level(double fullness) {
        return Double.isFinite(fullness)
            ? (int) Math.floor(Math.clamp(fullness, 0, 1) * 15)
            : 0;
    }

    /**
     * Block getSignal receives the direction from the receiver toward this
     * block.
     */
    public int signal(Direction queryDirection) {
        return outgoing[RelativeFace
            .of(RelativeFace.facing(owner.getBlockState()),
                queryDirection.getOpposite())
            .ordinal()];
    }

    /**
     * Read faces and configured Emit faces accept dust. Uses the
     * receiver-to-block query direction from {@link #signal(Direction)}.
     */
    public boolean connects(@Nullable Direction queryDirection) {
        if (queryDirection == null)
            return false;
        int face = RelativeFace.of(RelativeFace.facing(owner.getBlockState()),
            queryDirection.getOpposite()).ordinal();
        return connects(face);
    }

    private boolean connects(int face) {
        return mode(face) == Mode.READ
            || mode(face) == Mode.EMIT && source(face) >= 0;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putIntArray("modes", modes);
        tag.putIntArray("lower", lower);
        tag.putIntArray("upper", upper);
        int mask = 0, invertMask = 0;
        for (int i = 0; i < 6; i++) {
            if (outside[i])
                mask |= 1 << i;
            if (inverted[i])
                invertMask |= 1 << i;
            tag.putString("source_" + i, sourceIds[i]);
        }
        tag.putInt("outside", mask);
        tag.putInt("inverted", invertMask);
        tag.putBoolean("legacy_any", legacyAny);
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        var saved = tag.getIntArray("modes");
        var lows = tag.getIntArray("lower");
        var highs = tag.getIntArray("upper");
        int mask = tag.contains("outside")
            ? tag.getInt("outside")
            : tag.getInt("without");
        for (int i = 0; i < 6; i++) {
            modes[i] = saved.length == 6 && saved[i] >= 0
                && saved[i] < MODES.length ? saved[i] : 0;
            lower[i] = lows.length == 6 ? Math.clamp(lows[i], 0, 15) : 1;
            upper[i] = highs.length == 6
                ? Math.clamp(highs[i], lower[i], 15)
                : 15;
            outside[i] = (mask & 1 << i) != 0;
            inverted[i] = (tag.getInt("inverted") & 1 << i) != 0;
            sourceIds[i] = tag.getString(
                tag.contains("source_" + i) ? "source_" + i : "group_" + i);
            sourceIndexes[i] = findSource(sourceIds[i]);
        }
        legacyAny = tag.getBoolean("legacy_any");
        revision++;
    }

    public void migrate(int oldMode) {
        Arrays.fill(modes,
            oldMode == 1 || oldMode == 2
                ? Mode.READ.ordinal()
                : Mode.IGNORE.ordinal());
        Arrays.fill(lower, 1);
        Arrays.fill(upper, 15);
        Arrays.fill(outside, oldMode == 1);
        Arrays.fill(inverted, false);
        Arrays.fill(sourceIds, "");
        Arrays.fill(sourceIndexes, -1);
        changed();
        legacyAny = oldMode == 1;
    }
}
