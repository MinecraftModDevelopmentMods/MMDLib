package zone.moddev.mc.lib.machine.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;

import org.junit.jupiter.api.Test;

class WorkTargetsTest {
    @Test
    void tracksChangingTargetsAndClearsOnPauseIdleOrHostSuspension() {
        var source = new ArrayList<BlockPos>();
        var active = new AtomicBoolean(true);
        var feature = new WorkTargets("work", () -> source, active::get);
        var host = new FeatureHost(() -> {
        }).add(feature);
        var mutable = new BlockPos.MutableBlockPos(1, 2, 3);
        source.add(mutable);
        source.add(new BlockPos(1, 2, 3));
        feature.tick(host);
        mutable.set(4, 5, 6);
        assertEquals(List.of(new BlockPos(1, 2, 3)), feature.targets());
        source.clear();
        source.add(new BlockPos(7, 8, 9));
        feature.tick(host);
        assertEquals(source, feature.targets());
        active.set(false);
        feature.tick(host);
        assertTrue(feature.targets().isEmpty());
        active.set(true);
        feature.tick(host);
        feature.suspended(host);
        assertTrue(feature.targets().isEmpty());
        feature.tick(host);
        source.clear();
        feature.tick(host);
        assertTrue(feature.targets().isEmpty());
    }

    @Test
    void trackingClientsReceiveUpdatesAndClearsButDiskNeverRestoresActiveWork() {
        var source = new ArrayList<>(List.of(new BlockPos(20, 64, -20)));
        var feature = new WorkTargets("work", () -> source, () -> true);
        var host = new FeatureHost(() -> {
        }).add(feature);
        var remote = new WorkTargets("work", List::of, () -> false);
        var client = new FeatureHost(() -> {
        }).add(remote);
        feature.tick(host);
        client.loadOnClient(NbtOps.INSTANCE,
            host.saveForClient(NbtOps.INSTANCE));
        assertEquals(source, remote.targets());
        var disk = host.save(NbtOps.INSTANCE);
        client.load(NbtOps.INSTANCE, disk);
        assertTrue(remote.targets().isEmpty());
        client.loadOnClient(NbtOps.INSTANCE,
            host.saveForClient(NbtOps.INSTANCE));
        source.clear();
        feature.tick(host);
        client.loadOnClient(NbtOps.INSTANCE,
            host.saveForClient(NbtOps.INSTANCE));
        assertTrue(remote.targets().isEmpty());
    }

    @Test
    void capsProviderListsAndRejectsOversizedWireData() {
        var source = java.util.stream.IntStream
            .range(0, WorkTargets.MAX_TARGETS + 1)
            .mapToObj(x -> new BlockPos(x, 0, 0)).toList();
        var feature = new WorkTargets("work", () -> source, () -> true);
        feature.tick(new FeatureHost(() -> {
        }).add(feature));
        assertEquals(WorkTargets.MAX_TARGETS, feature.targets().size());
        var oversized = BlockPos.CODEC.listOf()
            .encodeStart(NbtOps.INSTANCE, source).getOrThrow();
        assertTrue(WorkTargets.CODEC.parse(NbtOps.INSTANCE, oversized).error()
            .isPresent());
    }
}
