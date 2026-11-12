package zone.moddev.mc.lib.machine.multiblock;

import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** A physical member, with a stable key and a state authored facing north. */
public record StructureMember(String key, String role, BlockPos offset,
    BlockState state) {
    public StructureMember {
        if (key.isBlank() || key.length() > 128 || role.isBlank()
            || role.length() > 128)
            throw new IllegalArgumentException("Invalid member key/role");
        offset = offset.immutable();
        Objects.requireNonNull(state);
        if (state.isAir())
            throw new IllegalArgumentException(
                "Air is a requirement, not a member");
    }
}
