package zone.moddev.mc.companion.test;

import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Panda;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import zone.moddev.mc.lib.api.entity.EntityPreviewCache;
import zone.moddev.mc.lib.api.entity.EntitySnapshot;
import zone.moddev.mc.lib.api.entity.EntitySnapshotPolicy;

@GameTestHolder("mmdlib_companion")
@PrefixGameTestTemplate(false)
public final class EntityPreviewTests {
    @GameTest(template = "empty")
    public static void customNamesRemainComponentsAndMalformedNamesAreIgnored(
        GameTestHelper h) {
        var data = new CompoundTag();
        data.putString("CustomName",
            "{\"translate\":\"test.pet\",\"with\":[\"Bessie\"]}");
        var name = new EntitySnapshot(data).customName().orElseThrow();
        h.assertTrue(name.equals(Component.translatable("test.pet", "Bessie")),
            "Saved names preserve translatable component arguments");
        data.putString("CustomName", "{");
        h.assertTrue(new EntitySnapshot(data).customName().isEmpty(),
            "Malformed custom names are ignored");
        h.assertTrue(EntitySnapshot.EMPTY.customName().isEmpty(),
            "Empty snapshots have no custom name");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pairedPolicyPreservesExtraPoseAcrossRestoreAndPreviewUpdates(
        GameTestHelper h) {
        var policy = new EntitySnapshotPolicy<>(Panda.class, Panda::isAlive,
            (panda, data) -> data.putBoolean("test:sitting", panda.isSitting()),
            (panda, data) -> panda.sit(data.getBoolean("test:sitting")));
        var source = EntityType.PANDA.create(h.getLevel());
        source.sit(true);
        var snapshot = policy.capture(source);
        var restored = policy.createDetached(snapshot, h.getLevel())
            .orElseThrow();
        h.assertTrue(
            restored.isSitting() && restored.getUUID().equals(source.getUUID())
                && h.getLevel().getEntity(source.getUUID()) == null,
            "Policy restores extra pose and identity without spawning");
        var cache = new EntityPreviewCache<>(policy, 1);
        h.assertTrue(cache.get(h.getLevel(), snapshot).isSitting(),
            "Preview uses the same restore policy");
        source.sit(false);
        h.assertTrue(
            policy.updateDetached(restored, policy.capture(source))
                && !restored.isSitting(),
            "Existing detached preview updates using the same restore hook");
        var other = EntityType.PANDA.create(h.getLevel());
        h.assertTrue(
            !policy.updateDetached(restored, policy.capture(other))
                && restored.getUUID().equals(source.getUUID()),
            "A different identity cannot overwrite a preview");
        var rejecting = new EntitySnapshotPolicy<>(Panda.class, panda -> false,
            (panda, data) -> {
            }, (panda, data) -> {
            });
        h.assertTrue(
            rejecting.capture(source).isEmpty()
                && rejecting.createDetached(snapshot, h.getLevel()).isEmpty(),
            "Policy applies to capture and restore");
        var broken = new EntitySnapshotPolicy<>(Panda.class, Panda::isAlive,
            (panda, data) -> {
            }, (panda, data) -> {
                throw new IllegalArgumentException("bad pose");
            });
        h.assertTrue(
            broken.createDetached(snapshot, h.getLevel()).isEmpty()
                && !broken.updateDetached(restored, snapshot),
            "Restore hook failures report failure");
        cache.clear();
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void previewsCacheBySnapshotWithoutSpawningAndReleaseTheirWorld(
        GameTestHelper h) {
        var cow = EntityType.COW.create(h.getLevel());
        var first = EntitySnapshot.capture(cow);
        var calls = new AtomicInteger();
        var cache = new EntityPreviewCache<Mob>(1, (snapshot, level) -> {
            calls.incrementAndGet();
            return snapshot.createDetached(level, Mob.class).orElse(null);
        });
        var preview = cache.get(h.getLevel(), first);
        h.assertTrue(
            preview != null && preview == cache.get(h.getLevel(), first),
            "Same snapshot reuses its preview");
        h.assertTrue(h.getLevel().getEntity(cow.getUUID()) == null,
            "Preview never joins the world");
        var replacement = first.data();
        replacement.putFloat("Health", 3);
        var second = cache.get(h.getLevel(), new EntitySnapshot(replacement));
        h.assertTrue(second != preview, "Changed payload rebuilds preview");
        cache.get(h.getLevel(), first);
        h.assertTrue(calls.get() == 3,
            "Bounded cache evicts least recently used snapshots");
        cache.get(null, first);
        cache.get(h.getLevel(), first);
        h.assertTrue(calls.get() == 4, "World changes discard cached previews");
        cache.clear();
        h.succeed();
    }
}
