package zone.moddev.mc.lib.machine.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtOps;

import org.junit.jupiter.api.Test;

class StoredProcessorMigrationTest {
    private static class Fixture extends StoredProcessor {
        final ProcessorState.Key<Integer> count = variables
            .integer("count", 0, 4, 1).synced().define();
        final ProcessorState.Key<String> label = variables
            .string("label", "empty").synced().define();
        final ProcessorTimer timer = variables.timer("cooldown", 10,
            ProcessorTimer.Policy.IDLE);
        final List<ProcessorState.DecodeFailure> rejected = new ArrayList<>();
        final List<Boolean> clients = new ArrayList<>();
        int migrations;
        boolean failMigration;

        protected int dataVersion() {
            return 2;
        }
        protected CompoundTag migrateState(int fromVersion, CompoundTag data) {
            migrations++;
            if (failMigration) {
                data.putString("label", "partial migration");
                throw new IllegalArgumentException("No translation");
            }
            if (fromVersion < 1 && data.contains("old_count")) {
                data.putInt("count", Math.min(4, data.getInt("old_count")));
                data.remove("old_count");
            }
            if (fromVersion < 2 && data.contains("cooldown"))
                data.putInt("cooldown", Math.min(10, data.getInt("cooldown")));
            return data;
        }
        protected void invalidState(ProcessorState.DecodeFailure failure,
            boolean client) {
            rejected.add(failure);
            clients.add(client);
        }
        public boolean canStart(ProcessorState state,
            ProcessorMachine machine) {
            return false;
        }
        public ProcessState process(ProcessorState state,
            ProcessorMachine machine, int delta) {
            return ProcessState.IDLE;
        }
    }

    private static Fixture fixture() {
        var processor = new Fixture();
        processor.initialize("legacy", null);
        return processor;
    }

    @Test
    void unversionedDiskDataMigratesBeforeDecodingAndResavesAtCurrentVersion() {
        var processor = fixture();
        var original = new CompoundTag();
        original.putInt("old_count", 20);
        original.putInt("cooldown", 40);
        original.putString("label", "kept");
        var host = new FeatureHost(() -> {
        }).add(processor);
        var data = new CompoundTag();
        data.put("legacy", original);
        host.load(NbtOps.INSTANCE, data);
        assertEquals(1, processor.migrations);
        assertEquals(4, processor.variables.get(processor.count));
        assertEquals(10, processor.timer.remaining());
        assertTrue(processor.rejected.isEmpty());
        assertEquals(40, original.getInt("cooldown"));
        assertTrue(original.contains("old_count"));
        var saved = processor.state();
        assertEquals(2, saved.getInt(StoredProcessor.DATA_VERSION));
        processor.restore(saved);
        assertEquals(1, processor.migrations);
    }

    @Test
    void failedMigrationFallsBackToUnmodifiedFieldsAndReportsInvalidValues() {
        var processor = fixture();
        processor.failMigration = true;
        var data = new CompoundTag();
        data.putInt("count", 99);
        data.putInt("cooldown", 40);
        data.putString("label", "original");
        processor.restore(data);
        assertEquals(1, processor.variables.get(processor.count));
        assertEquals(0, processor.timer.remaining());
        assertEquals("original", processor.variables.get(processor.label));
        assertEquals(List.of("count", "cooldown"), processor.rejected.stream()
            .map(ProcessorState.DecodeFailure::variable).toList());
        assertEquals(IntTag.valueOf(99), processor.rejected.getFirst().raw());
        assertEquals(List.of(false, false), processor.clients);
        assertEquals("original", data.getString("label"));
    }

    @Test
    void badClientFieldsDefaultWithoutRunningDiskMigrationsOrChangingPrivateState() {
        var processor = fixture();
        processor.timer.start(7);
        var host = new FeatureHost(() -> {
        }).add(processor);
        var snapshot = processor.clientState();
        snapshot.putInt("count", 99);
        snapshot.putString("status", "OLD_STATUS");
        snapshot.putString("label", "updated");
        snapshot.putInt("cooldown", 99);
        var packet = new CompoundTag();
        packet.put("legacy", snapshot);
        host.loadOnClient(NbtOps.INSTANCE, packet);
        assertEquals(0, processor.migrations);
        assertEquals(1, processor.variables.get(processor.count));
        assertEquals("updated", processor.variables.get(processor.label));
        assertEquals(7, processor.timer.remaining());
        assertEquals(Processor.ProcessState.IDLE, processor.executionStatus());
        assertEquals(List.of(true, true), processor.clients);
    }
}
