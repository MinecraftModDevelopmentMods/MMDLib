package zone.moddev.mc.lib.gui.model;

import java.util.Objects;

import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;

/** Immutable saved entity information for a display-only portrait. */
public final class EntityPortraitData {
    public static final EntityPortraitData EMPTY = new EntityPortraitData(
        new CompoundTag());
    public static final Codec<EntityPortraitData> CODEC = CompoundTag.CODEC
        .xmap(EntityPortraitData::new, EntityPortraitData::data);
    private final CompoundTag data;

    /**
     * Includes the entity's namespaced {@code id}; never retains the caller's
     * mutable tag.
     */
    public EntityPortraitData(CompoundTag data) {
        this.data = Objects.requireNonNull(data).copy();
    }
    public CompoundTag data() {
        return data.copy();
    }
    public boolean isEmpty() {
        return data.isEmpty();
    }
    @Override
    public boolean equals(Object other) {
        return other instanceof EntityPortraitData portrait
            && data.equals(portrait.data);
    }
    @Override
    public int hashCode() {
        return data.hashCode();
    }
}
