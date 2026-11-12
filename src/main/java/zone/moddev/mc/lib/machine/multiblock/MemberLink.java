package zone.moddev.mc.lib.machine.multiblock;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** Persisted reciprocal ownership, never a live controller reference. */
public record MemberLink(ResourceLocation dimension, BlockPos controller, UUID formation,
    String member) {
    public MemberLink {
        java.util.Objects.requireNonNull(dimension);
        java.util.Objects.requireNonNull(formation);
        controller = controller.immutable();
        if (member.isBlank() || member.length() > 128)
            throw new IllegalArgumentException("Invalid member key");
    }
    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("dimension", dimension.toString());
        tag.putLong("controller", controller.asLong());
        tag.putUUID("formation", formation);
        tag.putString("member", member);
        return tag;
    }
    public static MemberLink load(CompoundTag tag) {
        return new MemberLink(ResourceLocation.parse(tag.getString("dimension")),
            BlockPos.of(tag.getLong("controller")), tag.getUUID("formation"),
            tag.getString("member"));
    }
}
