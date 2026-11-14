package zone.moddev.mc.lib.machine.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.lib.api.entity.EntitySnapshot;

class EntitySnapshotTest {
    @Test
    void snapshotAndCodecKeepIndependentDataAndIdentity() {
        var id = UUID.randomUUID();
        var data = new CompoundTag();
        data.putString("id", "minecraft:cow");
        data.putUUID("UUID", id);
        var snapshot = new EntitySnapshot(data);
        data.putString("id", "minecraft:pig");
        snapshot.data().remove("UUID");
        assertEquals(ResourceLocation.parse("minecraft:cow"),
            snapshot.typeId().orElseThrow());
        assertEquals(id, snapshot.uuid().orElseThrow());
        var encoded = EntitySnapshot.CODEC
            .encodeStart(NbtOps.INSTANCE, snapshot).getOrThrow();
        assertEquals(snapshot,
            EntitySnapshot.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow());
        assertTrue(EntitySnapshot.EMPTY.uuid().isEmpty());
        assertTrue(EntitySnapshot.EMPTY.typeId().isEmpty());
    }

    @Test
    void missingBlankAndMalformedTypeIdsAreAbsent() {
        var data = new CompoundTag();
        assertTrue(new EntitySnapshot(data).typeId().isEmpty());
        for (var id : java.util.List.of("", " ", "not an id")) {
            data.putString("id", id);
            assertTrue(new EntitySnapshot(data).typeId().isEmpty());
        }
    }
}
