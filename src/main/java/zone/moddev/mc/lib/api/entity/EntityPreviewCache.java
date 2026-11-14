package zone.moddev.mc.lib.api.entity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Bounded, owner-scoped cache of detached previews, including unsuccessful
 * decodes.
 */
public final class EntityPreviewCache<T extends Entity> {
    private final int capacity;
    private final BiFunction<EntitySnapshot, Level, T> loader;
    private final Map<EntitySnapshot, T> entries = new LinkedHashMap<>(16, .75f,
        true);
    private Level level;

    public EntityPreviewCache(Class<T> type, int capacity) {
        this(capacity, (snapshot, world) -> snapshot.createDetached(world, type)
            .orElse(null));
    }

    public EntityPreviewCache(EntitySnapshotPolicy<T> policy, int capacity) {
        this(capacity, (snapshot, world) -> policy
            .createDetached(snapshot, world).orElse(null));
    }

    /**
     * Custom loaders can restore mod-specific poses; they must never spawn or
     * tick the entity.
     */
    public EntityPreviewCache(int capacity,
        BiFunction<EntitySnapshot, Level, T> loader) {
        if (capacity < 1 || capacity > 256)
            throw new IllegalArgumentException(
                "Preview capacity must be 1..256");
        this.capacity = capacity;
        this.loader = Objects.requireNonNull(loader);
    }

    public T get(Level current, EntitySnapshot snapshot) {
        Objects.requireNonNull(snapshot);
        if (current != level) {
            clear();
            level = current;
        }
        if (level == null || snapshot.isEmpty())
            return null;
        if (!entries.containsKey(snapshot)) {
            T entity = null;
            try {
                entity = loader.apply(snapshot, level);
                if (entity != null) {
                    entity.setPos(0, 0, 0);
                    var size = entity.getDimensions(entity.getPose());
                    if (entity.level() != level || !Float.isFinite(size.width())
                        || !Float.isFinite(size.height()) || size.width() <= 0
                        || size.height() <= 0)
                        entity = null;
                }
            } catch (RuntimeException malformed) {
                entity = null;
            }
            entries.put(snapshot, entity);
            if (entries.size() > capacity)
                entries.remove(entries.keySet().iterator().next());
        }
        return entries.get(snapshot);
    }

    /** Call when the owning screen, renderer session or world is released. */
    public void clear() {
        entries.clear();
        level = null;
    }
}
