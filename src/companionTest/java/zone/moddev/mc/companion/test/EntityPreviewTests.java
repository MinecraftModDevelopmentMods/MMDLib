package zone.moddev.mc.companion.test;

import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import zone.moddev.mc.lib.api.entity.EntityPreviewCache;
import zone.moddev.mc.lib.api.entity.EntitySnapshot;

@GameTestHolder("mmdlib_companion")
@PrefixGameTestTemplate(false)
public final class EntityPreviewTests {
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
