package zone.moddev.mc.lib.api.entity;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Immutable serialized entity data, independent of screens, machines or
 * ownership policy.
 */
public final class EntitySnapshot {
    public static final EntitySnapshot EMPTY = new EntitySnapshot(
        new CompoundTag());
    public static final Codec<EntitySnapshot> CODEC = CompoundTag.CODEC
        .xmap(EntitySnapshot::new, EntitySnapshot::data);
    private final CompoundTag data;

    public EntitySnapshot(CompoundTag data) {
        this.data = Objects.requireNonNull(data).copy();
    }

    public static EntitySnapshot capture(Entity entity) {
        return capture(entity, ignored -> {
        });
    }

    /**
     * Extensions can preserve extra pose data or remove links inappropriate for
     * transport.
     */
    public static EntitySnapshot capture(Entity entity,
        Consumer<CompoundTag> customize) {
        var tag = new CompoundTag();
        if (!entity.save(tag))
            return EMPTY;
        customize.accept(tag);
        return new EntitySnapshot(tag);
    }

    public CompoundTag data() {
        return data.copy();
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    public Optional<ResourceLocation> typeId() {
        return Optional
            .ofNullable(ResourceLocation.tryParse(data.getString("id")));
    }

    public Optional<UUID> uuid() {
        return data.hasUUID("UUID")
            ? Optional.of(data.getUUID("UUID"))
            : Optional.empty();
    }

    public Optional<EntityType<?>> type() {
        return EntityType.by(data);
    }

    /**
     * Reconstruct one entity without spawning or ticking it. Passenger trees
     * are not supported.
     */
    public <T extends Entity> Optional<T> createDetached(Level level,
        Class<T> expected) {
        if (level == null || isEmpty() || data.contains("Passengers"))
            return Optional.empty();
        try {
            var entity = type().map(type -> type.create(level)).orElse(null);
            if (!expected.isInstance(entity))
                return Optional.empty();
            entity.load(data());
            return Optional.of(expected.cast(entity));
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EntitySnapshot snapshot
            && data.equals(snapshot.data);
    }

    @Override
    public int hashCode() {
        return data.hashCode();
    }
}
