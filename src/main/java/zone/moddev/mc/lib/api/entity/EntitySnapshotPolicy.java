package zone.moddev.mc.lib.api.entity;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Paired capture/restore extensions shared by transport, consumers and detached
 * previews.
 */
public final class EntitySnapshotPolicy<T extends Entity> {
    private final Class<T> type;
    private final Predicate<T> accepts;
    private final BiConsumer<T, CompoundTag> capture, restore;

    public EntitySnapshotPolicy(Class<T> type, Predicate<T> accepts,
        BiConsumer<T, CompoundTag> capture,
        BiConsumer<T, CompoundTag> restore) {
        this.type = Objects.requireNonNull(type);
        this.accepts = Objects.requireNonNull(accepts);
        this.capture = Objects.requireNonNull(capture);
        this.restore = Objects.requireNonNull(restore);
    }

    public EntitySnapshot capture(T entity) {
        return accepts.test(entity)
            ? EntitySnapshot.capture(entity,
                data -> capture.accept(entity, data))
            : EntitySnapshot.EMPTY;
    }

    public Optional<T> createDetached(EntitySnapshot snapshot, Level level) {
        return snapshot.createDetached(level, type).filter(entity -> {
            try {
                restore.accept(entity, snapshot.data());
                return accepts.test(entity);
            } catch (RuntimeException malformed) {
                return false;
            }
        });
    }

    /**
     * Refresh an existing detached entity of the same type and UUID, preserving
     * renderer identity. Never use on a world entity. On failure discard the
     * preview: malformed data or a failing hook may have partially changed it.
     */
    public boolean updateDetached(T entity, EntitySnapshot snapshot) {
        if (snapshot.type().orElse(null) != entity.getType()
            || !snapshot.uuid().filter(entity.getUUID()::equals).isPresent()
            || snapshot.data().contains("Passengers"))
            return false;
        try {
            entity.load(snapshot.data());
            restore.accept(entity, snapshot.data());
            return accepts.test(entity);
        } catch (RuntimeException malformed) {
            return false;
        }
    }
}
